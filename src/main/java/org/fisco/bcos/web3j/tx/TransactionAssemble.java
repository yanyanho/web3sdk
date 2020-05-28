package org.fisco.bcos.web3j.tx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.fisco.bcos.web3j.abi.TypeReference;
import org.fisco.bcos.web3j.abi.datatypes.Type;
import org.fisco.bcos.web3j.protocol.core.methods.response.AbiDefinition;
import org.fisco.bcos.web3j.tx.txdecode.BaseException;
import org.fisco.bcos.web3j.tx.txdecode.ConstantProperties;
import org.fisco.bcos.web3j.tx.txdecode.ContractAbiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TransactionAssemble {

    private String funcName;
    private int groupId = 1;
    private List<Object> contractAbi = new ArrayList<>();
    private List<Object> funcParam = new ArrayList<>();



    public String transactionAssemble(String contractAbi, String funcName, List<Object> funcParam) throws TransactionAssembleException, BaseException {
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
        return "";
    }



    public static AbiDefinition getAbiDefinition(String name, String contractAbi) {
        JSONArray abiArr = JSONArray.parseArray(contractAbi);
        AbiDefinition result = null;
        for (Object object : abiArr) {
            AbiDefinition abiDefinition = JSON.parseObject(object.toString(), AbiDefinition.class);
            if (ConstantProperties.TYPE_FUNCTION.equals(abiDefinition.getType())
                    && name.equals(abiDefinition.getName())) {
                result = abiDefinition;
                break;
            }
        }
        return result;
    }
}
