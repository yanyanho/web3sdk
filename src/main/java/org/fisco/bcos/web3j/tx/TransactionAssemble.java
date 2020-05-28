package org.fisco.bcos.web3j.tx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.fisco.bcos.channel.client.TransactionSucCallback;
import org.fisco.bcos.web3j.abi.FunctionEncoder;
import org.fisco.bcos.web3j.abi.FunctionReturnDecoder;
import org.fisco.bcos.web3j.abi.TypeReference;
import org.fisco.bcos.web3j.abi.datatypes.Bool;
import org.fisco.bcos.web3j.abi.datatypes.Function;
import org.fisco.bcos.web3j.abi.datatypes.Type;
import org.fisco.bcos.web3j.crypto.ExtendedRawTransaction;
import org.fisco.bcos.web3j.crypto.ExtendedTransactionEncoder;
import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.core.DefaultBlockParameterName;
import org.fisco.bcos.web3j.protocol.core.Request;
import org.fisco.bcos.web3j.protocol.core.methods.request.Transaction;
import org.fisco.bcos.web3j.protocol.core.methods.response.AbiDefinition;
import org.fisco.bcos.web3j.protocol.core.methods.response.SendTransaction;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.txdecode.BaseException;
import org.fisco.bcos.web3j.tx.txdecode.ConstantProperties;
import org.fisco.bcos.web3j.tx.txdecode.ContractAbiUtil;
import org.fisco.bcos.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.fisco.bcos.web3j.tx.gas.DefaultGasProvider.GAS_LIMIT;
import static org.fisco.bcos.web3j.tx.gas.DefaultGasProvider.GAS_PRICE;

public class TransactionAssemble {

    private  Web3j web3j;
    private String userAddress;
    private int transMaxWait = 30;

    public String getUserAddress() {
        return userAddress;
    }

    public void setUserAddress(String userAddress) {
        this.userAddress = userAddress;
    }

    public Web3j getWeb3j() {
        return web3j;
    }

    public void setWeb3j(Web3j web3j) {
        this.web3j = web3j;
    }

    public TransactionAssemble(Web3j web3j, String userAddress) {
        this.web3j = web3j;
        this.userAddress = userAddress;
    }
    public  String transactionAssemble(String contractAbi, String funcName,int groupId, String contractAddress, List<Object> funcParam) throws IOException, BaseException {
       AbiDefinition abiDefinition =getAbiDefinition(funcName, contractAbi);
        if (Objects.isNull(abiDefinition)) {
            throw new TransactionAssembleException("contract funcName is error");
        }

        List<String> funcInputTypes = ContractAbiUtil.getFuncInputType(abiDefinition);
        // check param match inputs
        if (funcInputTypes.size() != funcParam.size()) {
            throw new TransactionAssembleException("contract funcParam size is error");
        }
        List<Type> finalInputs = AbiUtil.inputFormat(funcInputTypes, funcParam);
        // output format
        List<String> funOutputTypes = AbiUtil.getFuncOutputType(abiDefinition);
        List<TypeReference<?>> finalOutputs = AbiUtil.outputFormat(funOutputTypes);
        boolean isConstant = abiDefinition.isConstant();

        Function function = new Function(funcName, finalInputs, finalOutputs);
        String encodedFunction = FunctionEncoder.encode(function);

        if(isConstant) {
           return encodedFunction;
        } else {
            Random r = new Random();
            BigInteger randomid = new BigInteger(250, r);

            BigInteger blockLimit = web3j.getBlockNumberCache();

            ExtendedRawTransaction extendedRawTransaction =
                    ExtendedRawTransaction.createTransaction(randomid, GAS_PRICE,
                            GAS_LIMIT, blockLimit, contractAddress, BigInteger.ZERO, encodedFunction,
                            new BigInteger("1"), BigInteger.valueOf(groupId), "");
            byte[] encodedTransaction = ExtendedTransactionEncoder.encode(extendedRawTransaction);
            String encodedDataStr = Numeric.toHexString(encodedTransaction);
            return encodedDataStr;
        }
    }


     public  Boolean checkMethodIsConstant(String contractAbi, String funcName) throws TransactionAssembleException {
         AbiDefinition abiDefinition =getAbiDefinition(funcName, contractAbi);
         if (Objects.isNull(abiDefinition)) {
             throw new TransactionAssembleException("contract funcName is error");
         }
         return abiDefinition.isConstant();
     }

    public static AbiDefinition getAbiDefinition(String functionName, String contractAbi) {
        JSONArray abiArr = JSONArray.parseArray(contractAbi);
        AbiDefinition result = null;
        for (Object object : abiArr) {
            AbiDefinition abiDefinition = JSON.parseObject(object.toString(), AbiDefinition.class);
            if (ConstantProperties.TYPE_FUNCTION.equals(abiDefinition.getType())
                    && functionName.equals(abiDefinition.getName())) {
                result = abiDefinition;
                break;
            }
        }
        return result;
    }


    public TransactionReceipt  sendSignedTransaction(String signedStr) throws InterruptedException, ExecutionException, TimeoutException, IOException {
        final CompletableFuture<TransactionReceipt> transFuture = new CompletableFuture<>();
        sendMessage(web3j, signedStr, transFuture);
        TransactionReceipt receipt = transFuture.get(transMaxWait, TimeUnit.SECONDS);
        return receipt;
    }

    public Object  sendQueryTransaction(String encodeStr, String contractAddress,String funcName, String contractAbi) throws IOException, BaseException {
        String callOutput = web3j
                .call(Transaction.createEthCallTransaction(userAddress,
                        contractAddress, encodeStr), DefaultBlockParameterName.LATEST)
                .send().getValue().getOutput();

        AbiDefinition abiDefinition =getAbiDefinition(funcName, contractAbi);
        if (Objects.isNull(abiDefinition)) {
            throw new TransactionAssembleException("contract funcName is error");
        }

        List<String> funOutputTypes = AbiUtil.getFuncOutputType(abiDefinition);
        List<TypeReference<?>> finalOutputs = AbiUtil.outputFormat(funOutputTypes);
        List<Type> typeList =  FunctionReturnDecoder.decode(callOutput, finalOutputs);
        Object response;
        if (typeList.size() > 0) {
            response = AbiUtil.callResultParse(funOutputTypes, typeList);
        } else {
            response = typeList;
        }
        return response;
    }

    public void sendMessage(Web3j web3j, String signMsg,
                            final CompletableFuture<TransactionReceipt> future) throws IOException {
        Request<?, SendTransaction> request = web3j.sendRawTransaction(signMsg);
        request.setNeedTransCallback(true);
        request.setTransactionSucCallback(new TransactionSucCallback() {
            @Override
            public void onResponse(TransactionReceipt receipt) {
                future.complete(receipt);
                return;
            }
        });
        request.send();
    }

}
