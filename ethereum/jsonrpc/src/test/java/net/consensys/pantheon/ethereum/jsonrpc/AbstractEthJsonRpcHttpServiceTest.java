package net.consensys.pantheon.ethereum.jsonrpc;

import static net.consensys.pantheon.ethereum.core.InMemoryWorldState.createInMemoryWorldStateArchive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.chain.MutableBlockchain;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockImporter;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.PendingTransactions;
import net.consensys.pantheon.ethereum.core.Synchronizer;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.core.TransactionPool;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.eth.EthProtocol;
import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration.RpcApis;
import net.consensys.pantheon.ethereum.jsonrpc.internal.filter.FilterIdGenerator;
import net.consensys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.mainnet.HeaderValidationMode;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;
import net.consensys.pantheon.ethereum.mainnet.ValidationResult;
import net.consensys.pantheon.ethereum.p2p.api.P2PNetwork;
import net.consensys.pantheon.ethereum.p2p.wire.Capability;
import net.consensys.pantheon.ethereum.util.RawBlockIterator;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.services.kvstore.KeyValueStorage;

import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.Vertx;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

public abstract class AbstractEthJsonRpcHttpServiceTest {

  protected static ProtocolSchedule<Void> PROTOCOL_SCHEDULE;

  protected static List<Block> BLOCKS;

  protected static Block GENESIS_BLOCK;

  protected static GenesisConfig<?> GENESIS_CONFIG;

  protected final Vertx vertx = Vertx.vertx();

  protected JsonRpcHttpService service;

  protected OkHttpClient client;

  protected String baseUrl;

  protected final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  protected final String CLIENT_VERSION = "TestClientVersion/0.1.0";

  protected final String NET_VERSION = "6986785976597";

  protected static final Collection<RpcApis> JSON_RPC_APIS =
      Arrays.asList(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3);

  protected MutableBlockchain blockchain;

  protected WorldStateArchive stateArchive;

  protected FilterManager filterManager;

  protected ProtocolContext<Void> context;

  @BeforeClass
  public static void setupConstants() throws Exception {
    PROTOCOL_SCHEDULE = MainnetProtocolSchedule.create();

    final URL blocksUrl =
        EthJsonRpcHttpBySpecTest.class
            .getClassLoader()
            .getResource("net/consensys/pantheon/ethereum/jsonrpc/jsonRpcTestBlockchain.blocks");

    final URL genesisJsonUrl =
        EthJsonRpcHttpBySpecTest.class
            .getClassLoader()
            .getResource("net/consensys/pantheon/ethereum/jsonrpc/jsonRpcTestGenesis.json");

    assertThat(blocksUrl).isNotNull();
    assertThat(genesisJsonUrl).isNotNull();

    BLOCKS = new ArrayList<>();
    try (final RawBlockIterator iterator =
        new RawBlockIterator(
            Paths.get(blocksUrl.toURI()),
            rlp -> BlockHeader.readFrom(rlp, MainnetBlockHashFunction::createHash))) {
      while (iterator.hasNext()) {
        BLOCKS.add(iterator.next());
      }
    }

    final String gensisjson = Resources.toString(genesisJsonUrl, Charsets.UTF_8);

    GENESIS_BLOCK = BLOCKS.get(0);
    GENESIS_CONFIG = GenesisConfig.fromJson(gensisjson, PROTOCOL_SCHEDULE);
  }

  @Before
  public void setupTest() {
    final Synchronizer synchronizerMock = mock(Synchronizer.class);
    final P2PNetwork peerDiscoveryMock = mock(P2PNetwork.class);
    final TransactionPool transactionPoolMock = mock(TransactionPool.class);
    final EthHashMiningCoordinator miningCoordinatorMock = mock(EthHashMiningCoordinator.class);
    when(transactionPoolMock.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());
    final PendingTransactions pendingTransactionsMock = mock(PendingTransactions.class);
    when(transactionPoolMock.getPendingTransactions()).thenReturn(pendingTransactionsMock);
    final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    stateArchive = createInMemoryWorldStateArchive();
    GENESIS_CONFIG.writeStateTo(stateArchive.getMutable(Hash.EMPTY_TRIE_HASH));

    blockchain =
        new DefaultMutableBlockchain(
            GENESIS_BLOCK, keyValueStorage, MainnetBlockHashFunction::createHash);
    context = new ProtocolContext<>(blockchain, stateArchive, null);

    final BlockchainQueries blockchainQueries = new BlockchainQueries(blockchain, stateArchive);
    final FilterIdGenerator filterIdGenerator = mock(FilterIdGenerator.class);
    when(filterIdGenerator.nextId()).thenReturn("0x1");
    filterManager = new FilterManager(blockchainQueries, transactionPoolMock, filterIdGenerator);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);

    final Map<String, JsonRpcMethod> methods =
        new JsonRpcMethodsFactory()
            .methods(
                CLIENT_VERSION,
                NET_VERSION,
                peerDiscoveryMock,
                blockchainQueries,
                synchronizerMock,
                MainnetProtocolSchedule.create(),
                filterManager,
                transactionPoolMock,
                miningCoordinatorMock,
                supportedCapabilities,
                JSON_RPC_APIS);
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setPort(0);
    service = new JsonRpcHttpService(vertx, config, methods);
    service.start().join();

    client = new OkHttpClient();
    baseUrl = service.url();
  }

  @After
  public void shutdownServer() {
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
    service.stop().join();
    vertx.close();
  }

  protected void importBlock(final int n) {
    final Block block = BLOCKS.get(n);
    final ProtocolSpec<Void> protocolSpec =
        PROTOCOL_SCHEDULE.getByBlockNumber(block.getHeader().getNumber());
    final BlockImporter<Void> blockImporter = protocolSpec.getBlockImporter();
    blockImporter.importBlock(context, block, HeaderValidationMode.FULL);
  }
}
