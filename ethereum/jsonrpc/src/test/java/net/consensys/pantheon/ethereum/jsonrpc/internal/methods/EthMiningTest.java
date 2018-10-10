package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.blockcreation.EthHashBlockMiner;
import net.consensys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthMiningTest {

  @Mock private EthHashMiningCoordinator miningCoordinator;
  private EthMining<Void, EthHashBlockMiner> method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_mining";

  @Before
  public void setUp() {
    method = new EthMining<>(miningCoordinator);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnTrueWhenMiningCoordinatorExistsAndRunning() {
    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), true);
    when(miningCoordinator.isRunning()).thenReturn(true);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(miningCoordinator).isRunning();
    verifyNoMoreInteractions(miningCoordinator);
  }

  @Test
  public void shouldReturnFalseWhenMiningCoordinatorExistsAndDisabled() {
    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), false);
    when(miningCoordinator.isRunning()).thenReturn(false);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(miningCoordinator).isRunning();
    verifyNoMoreInteractions(miningCoordinator);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params);
  }
}
