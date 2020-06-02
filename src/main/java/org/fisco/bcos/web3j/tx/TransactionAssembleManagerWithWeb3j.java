package org.fisco.bcos.web3j.tx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.fisco.bcos.channel.client.TransactionSucCallback;
import org.fisco.bcos.web3j.abi.FunctionEncoder;
import org.fisco.bcos.web3j.abi.FunctionReturnDecoder;
import org.fisco.bcos.web3j.abi.TypeReference;
import org.fisco.bcos.web3j.abi.Utils;
import org.fisco.bcos.web3j.abi.datatypes.Function;
import org.fisco.bcos.web3j.abi.datatypes.Type;
import org.fisco.bcos.web3j.crypto.*;
import org.fisco.bcos.web3j.crypto.gm.sm2.SM2Sign;
import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.core.DefaultBlockParameterName;
import org.fisco.bcos.web3j.protocol.core.Request;
import org.fisco.bcos.web3j.protocol.core.methods.request.Transaction;
import org.fisco.bcos.web3j.protocol.core.methods.response.AbiDefinition;
import org.fisco.bcos.web3j.protocol.core.methods.response.SendTransaction;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.txdecode.BaseException;
import org.fisco.bcos.web3j.tx.txdecode.ConstantCode;
import org.fisco.bcos.web3j.tx.txdecode.ConstantProperties;
import org.fisco.bcos.web3j.tx.txdecode.ContractAbiUtil;
import org.fisco.bcos.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.fisco.bcos.web3j.tx.gas.DefaultGasProvider.GAS_LIMIT;
import static org.fisco.bcos.web3j.tx.gas.DefaultGasProvider.GAS_PRICE;
import static org.fisco.bcos.web3j.utils.ByteUtil.hexStringToBytes;

public class TransactionAssembleManagerWithWeb3j {

    private Web3j web3j;
    private String userAddress;
    private static int transMaxWait = 30;

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

    public int getTransMaxWait() {
        return transMaxWait;
    }

    public void setTransMaxWait(int transMaxWait) {
        this.transMaxWait = transMaxWait;
    }

    public TransactionAssembleManagerWithWeb3j(Web3j web3j, String userAddress) {
        this.web3j = web3j;
        this.userAddress = userAddress;
    }

    public String transactionAssembleForMethodInvoke(String contractAbi, int groupId, String contractAddress, String funcName, List<Object> funcParam) throws IOException, BaseException {
        AbiDefinition abiDefinition = getFunctionAbiDefinition(funcName, contractAbi);
        if (Objects.isNull(abiDefinition)) {
            throw new TransactionAssembleException("contract funcName is error");
        }

        List<String> funcInputTypes = ContractAbiUtil.getFuncInputType(abiDefinition);
        // check param match inputs
        if(funcParam == null) {
            funcParam =  new ArrayList<>();
        }
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

        if (isConstant) {
            return encodedFunction;
        } else {
            Random r = new Random();
            BigInteger randomid = new BigInteger(250, r);

            BigInteger blockLimit = web3j.getBlockNumberCache();

            ExtendedRawTransaction extendedRawTransaction = ExtendedRawTransaction.createTransaction(randomid, GAS_PRICE,
                            GAS_LIMIT, blockLimit, contractAddress, BigInteger.ZERO, encodedFunction,
                            new BigInteger("1"), BigInteger.valueOf(groupId), "");
            byte[] encodedTransaction = ExtendedTransactionEncoder.encode(extendedRawTransaction);
            String encodedDataStr = Numeric.toHexString(encodedTransaction);
            return encodedDataStr;
        }
    }

    public String transactionAssembleForDeploy(String contractAbi, String bytecodeBin, int groupId, List<Object> constructorParam) throws TransactionAssembleException, BaseException {
        String encodedConstructor = "";
        // input handle
        AbiDefinition abiDefinition = getConstructorAbiDefinition(contractAbi);
        if (abiDefinition.getInputs().size() != 0) {

            List<String> funcInputTypes = ContractAbiUtil.getFuncInputType(abiDefinition);
            if(constructorParam == null) {
                constructorParam =  new ArrayList<>();
            }
            // check param match inputs
            if (funcInputTypes.size() != constructorParam.size()) {
                throw new TransactionAssembleException("contract constructor funcParam size is error");
            }

            if (funcInputTypes != null && funcInputTypes.size() > 0) {
                if (funcInputTypes.size() == constructorParam.size()) {
                    List<Type> finalInputs = AbiUtil.inputFormat(funcInputTypes, constructorParam);
                    encodedConstructor = FunctionEncoder.encodeConstructor(finalInputs);
                } else {
                    throw new BaseException(ConstantCode.IN_FUNCPARAM_ERROR);
                }
            }
        }

        String encodedFunction = bytecodeBin + encodedConstructor;

        Random r = new Random();
        BigInteger randomid = new BigInteger(250, r);

        BigInteger blockLimit = web3j.getBlockNumberCache();

        ExtendedRawTransaction extendedRawTransaction =
                ExtendedRawTransaction.createTransaction(randomid, GAS_PRICE,
                        GAS_LIMIT, blockLimit, "", BigInteger.ZERO, encodedFunction,
                        new BigInteger("1"), BigInteger.valueOf(groupId), "");
        byte[] encodedTransaction = ExtendedTransactionEncoder.encode(extendedRawTransaction);
        String encodedDataStr = Numeric.toHexString(encodedTransaction);
        return encodedDataStr;

    }

    // TransactionReceipt just contains transactionHash when sync is false;
    public TransactionReceipt sendSignedTransaction(String signedStr, Boolean sync) throws InterruptedException, ExecutionException, TimeoutException, IOException {

        if (sync) {
            final CompletableFuture<TransactionReceipt> transFuture = new CompletableFuture<>();
            sendMessage(web3j, signedStr, transFuture);
            TransactionReceipt receipt = transFuture.get(transMaxWait, TimeUnit.SECONDS);
            return receipt;
        } else {
            TransactionReceipt transactionReceipt = new TransactionReceipt();
            web3j.sendRawTransaction(signedStr).sendAsync();
            transactionReceipt.setTransactionHash(Hash.sha3(signedStr));
            return transactionReceipt;
        }
    }

    public Object sendQueryTransaction(String encodeStr, String contractAddress, String funcName, String contractAbi) throws IOException, BaseException {
        String callOutput = web3j.call(Transaction.createEthCallTransaction(userAddress, contractAddress, encodeStr), DefaultBlockParameterName.LATEST)
                .send().getValue().getOutput();

        AbiDefinition abiDefinition = getFunctionAbiDefinition(funcName, contractAbi);
        if (Objects.isNull(abiDefinition)) {
            throw new TransactionAssembleException("contract funcName is error");
        }

        List<String> funOutputTypes = AbiUtil.getFuncOutputType(abiDefinition);
        List<TypeReference<?>> finalOutputs = AbiUtil.outputFormat(funOutputTypes);

        List<Type> typeList = FunctionReturnDecoder.decode(callOutput, Utils.convert(finalOutputs));
        Object response;
        if (typeList.size() > 0) {
            response = AbiUtil.callResultParse(funOutputTypes, typeList);
        } else {
            response = typeList;
        }
        return response;
    }

    public String signMessageByEncryptType(String hexMessage, ECKeyPair keyPair, int encryptType) {

        byte[] messageByte = hexStringToBytes(hexMessage);
        Sign.SignatureData signatureData;
        if (encryptType == 1) {
            signatureData = SM2Sign.sign(messageByte, keyPair);
        } else {
            ECDSASign ecdsaSign = new ECDSASign();
            signatureData = ecdsaSign.signMessage(messageByte, keyPair);
        }
        //   String signDataStr = signatureDataToStringByType(signatureData, encryptType);

        ExtendedRawTransaction extendedRawTransaction = ExtendedTransactionDecoder.decode(hexMessage);
        byte[] signedMessage = ExtendedTransactionEncoder.encode(extendedRawTransaction, signatureData);
        String signMsg = Numeric.toHexString(signedMessage);
        return signMsg;
    }

    public Boolean checkMethodIsConstant(String contractAbi, String funcName) throws TransactionAssembleException {
        AbiDefinition abiDefinition = getFunctionAbiDefinition(funcName, contractAbi);
        if (Objects.isNull(abiDefinition)) {
            throw new TransactionAssembleException("contract funcName is error");
        }
        return abiDefinition.isConstant();
    }

    public static AbiDefinition getFunctionAbiDefinition(String functionName, String contractAbi) {
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

    public static AbiDefinition getConstructorAbiDefinition(String contractAbi) {
        JSONArray abiArr = JSONArray.parseArray(contractAbi);
        AbiDefinition result = null;
        for (Object object : abiArr) {
            AbiDefinition abiDefinition = JSON.parseObject(object.toString(), AbiDefinition.class);
            if (ConstantProperties.TYPE_CONSTRUCTOR.equals(abiDefinition.getType())) {
                result = abiDefinition;
                break;
            }
        }
        return result;
    }


    public void sendMessage(Web3j web3j, String signMsg, final CompletableFuture<TransactionReceipt> future) throws IOException {
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


    public static String signatureDataToStringByType(Sign.SignatureData signatureData, int encryptType) {
        byte[] byteArr;
        if (encryptType == 1) {
            byteArr = sigData2ByteArrGuomi(signatureData);
        } else {
            byteArr = sigData2ByteArrECDSA(signatureData);
        }
        return Numeric.toHexString(byteArr, 0, byteArr.length, false);
    }

    private static byte[] sigData2ByteArrGuomi(Sign.SignatureData signatureData) {
        byte[] targetByteArr;
        targetByteArr = new byte[1 + signatureData.getR().length + signatureData.getS().length + 64];
        targetByteArr[0] = signatureData.getV();
        System.arraycopy(signatureData.getR(), 0, targetByteArr, 1, signatureData.getR().length);
        System.arraycopy(signatureData.getS(), 0, targetByteArr, signatureData.getR().length + 1,
                signatureData.getS().length);
        System.arraycopy(signatureData.getPub(), 0, targetByteArr,
                signatureData.getS().length + signatureData.getR().length + 1,
                signatureData.getPub().length);
        return targetByteArr;
    }

    private static byte[] sigData2ByteArrECDSA(Sign.SignatureData signatureData) {
        byte[] targetByteArr;
        targetByteArr = new byte[1 + signatureData.getR().length + signatureData.getS().length];
        targetByteArr[0] = signatureData.getV();
        System.arraycopy(signatureData.getR(), 0, targetByteArr, 1, signatureData.getR().length);
        System.arraycopy(signatureData.getS(), 0, targetByteArr, signatureData.getR().length + 1,
                signatureData.getS().length);
        return targetByteArr;
    }

    public static Sign.SignatureData stringToSignatureData(String signatureData) {
        byte[] byteArr = Numeric.hexStringToByteArray(signatureData);
        byte[] signR = new byte[32];
        System.arraycopy(byteArr, 1, signR, 0, signR.length);
        byte[] signS = new byte[32];
        System.arraycopy(byteArr, 1 + signR.length, signS, 0, signS.length);
        if (EncryptType.encryptType == 1) {
            byte[] pub = new byte[64];
            System.arraycopy(byteArr, 1 + signR.length + signS.length, pub, 0, pub.length);
            return new Sign.SignatureData(byteArr[0], signR, signS, pub);
        } else {
            return new Sign.SignatureData(byteArr[0], signR, signS);
        }
    }
}
