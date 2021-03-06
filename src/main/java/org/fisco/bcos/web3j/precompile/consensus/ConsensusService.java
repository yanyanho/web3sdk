package org.fisco.bcos.web3j.precompile.consensus;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.fisco.bcos.web3j.crypto.Credentials;
import org.fisco.bcos.web3j.precompile.common.PrecompiledCommon;
import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.gas.ContractGasProvider;
import org.fisco.bcos.web3j.tx.gas.StaticGasProvider;

public class ConsensusService {
    private static BigInteger gasPrice = new BigInteger("300000000");
    private static BigInteger gasLimit = new BigInteger("300000000");
    private static String ConsensusPrecompileAddress = "0x0000000000000000000000000000000000001003";
    private Web3j web3j;
    private Consensus consensus;

    public ConsensusService(Web3j web3j, Credentials credentials) {
        ContractGasProvider contractGasProvider = new StaticGasProvider(gasPrice, gasLimit);
        this.web3j = web3j;
        consensus =
                Consensus.load(ConsensusPrecompileAddress, web3j, credentials, contractGasProvider);
    }

    public String addSealer(String nodeID) throws Exception {
        TransactionReceipt receipt = addSealerAndRetReceipt(nodeID);
        return PrecompiledCommon.handleTransactionReceipt(receipt, web3j);
    }

    public TransactionReceipt addSealerAndRetReceipt(String nodeID) throws Exception {
        return consensus.addSealer(nodeID).send();
    }

    public String addObserver(String nodeID) throws Exception {
        TransactionReceipt receipt = addObserverAndRetReceipt(nodeID);
        return PrecompiledCommon.handleTransactionReceipt(receipt, web3j);
    }

    public TransactionReceipt addObserverAndRetReceipt(String nodeID) throws Exception {
        return consensus.addObserver(nodeID).send();
    }

    public String removeNode(String nodeId) throws Exception {
        List<String> groupPeers = web3j.getGroupPeers().send().getResult();
        if (!groupPeers.contains(nodeId)) {
            return PrecompiledCommon.transferToJson(PrecompiledCommon.GroupPeers);
        }
        TransactionReceipt receipt = null;
        try {
            receipt = removeNodeAndRetReceipt(nodeId);
        } catch (RuntimeException e) {
            // firstly remove node that sdk connected to the node, return the request that present
            // susscces
            // because the exception is throwed by getTransactionReceipt, we need ignore it.
            if (e.getMessage().contains("Don't send requests to this group")) {
                return PrecompiledCommon.transferToJson(0);
            } else {
                throw e;
            }
        }
        return PrecompiledCommon.handleTransactionReceipt(receipt, web3j);
    }

    public TransactionReceipt removeNodeAndRetReceipt(String nodeId) throws Exception {
        return consensus.remove(nodeId).send();
        /*List<String> groupPeers = web3j.getGroupPeers().send().getResult();
        if (!groupPeers.contains(nodeId)) {
            return PrecompiledCommon.transferToJson(PrecompiledCommon.GroupPeers);
        }
        TransactionReceipt receipt = new TransactionReceipt();
        try {
            receipt = consensus.remove(nodeId).send();
        } catch (RuntimeException e) {
            // firstly remove node that sdk connected to the node, return the request that present
            // susscces
            // because the exception is throwed by getTransactionReceipt, we need ignore it.
            if (e.getMessage().contains("Don't send requests to this group")) {
                return PrecompiledCommon.transferToJson(0);
            } else {
                throw e;
            }
        }
        return PrecompiledCommon.handleTransactionReceipt(receipt, web3j);
        */
    }

    private boolean isValidNodeID(String _nodeID) throws IOException, JsonProcessingException {
        boolean flag = false;
        List<String> nodeIDs = web3j.getNodeIDList().send().getResult();
        for (String nodeID : nodeIDs) {
            if (_nodeID.equals(nodeID)) {
                flag = true;
                break;
            }
        }
        return flag;
    }
}
