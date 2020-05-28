/*
 * Copyright 2014-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.fisco.bcos.web3j.tx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import org.fisco.bcos.web3j.abi.EventValues;
import org.fisco.bcos.web3j.abi.TypeReference;
import org.fisco.bcos.web3j.abi.datatypes.*;
import org.fisco.bcos.web3j.abi.datatypes.generated.AbiTypes;
import org.fisco.bcos.web3j.protocol.core.methods.response.AbiDefinition;
import org.fisco.bcos.web3j.protocol.core.methods.response.AbiDefinition.NamedType;
import org.fisco.bcos.web3j.protocol.core.methods.response.Log;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.txdecode.BaseException;
import org.fisco.bcos.web3j.tx.txdecode.ConstantProperties;
import org.fisco.bcos.web3j.tx.txdecode.ContractTypeUtil;
import org.fisco.bcos.web3j.utils.Numeric;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.*;

/**
 * ContractAbiUtil.
 * format abi types from String
 */
public class AbiUtil {

    /**
     * get constructor abi info.
     * 
     * @param contractAbi contractAbi
     * @return
     */
    public static AbiDefinition getAbiDefinition(String contractAbi) {
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

    /**
     * get function abi info.
     * 
     * @param name name
     * @param contractAbi contractAbi
     * @return
     */
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
    
    /**
     * get event abi info.
     * 
     * @param contractAbi contractAbi
     * @return
     */
    public static List<AbiDefinition> getEventAbiDefinitions(String contractAbi) {
        JSONArray abiArr = JSONArray.parseArray(contractAbi);
        List<AbiDefinition> result = new ArrayList<>();
        for (Object object : abiArr) {
            AbiDefinition abiDefinition = JSON.parseObject(object.toString(), AbiDefinition.class);
            if (ConstantProperties.TYPE_EVENT.equals(abiDefinition.getType())) {
                result.add(abiDefinition);
            } 
        }
        return result;
    }

    /**
     * getFuncInputType.
     * 
     * @param abiDefinition abiDefinition
     * @return
     */
    public static List<String> getFuncInputType(AbiDefinition abiDefinition) {
        List<String> inputList = new ArrayList<>();
        if (abiDefinition != null) {
            List<NamedType> inputs = abiDefinition.getInputs();
            for (NamedType input : inputs) {
                inputList.add(input.getType());
            }
        }
        return inputList;
    }

    /**
     * getFuncOutputType.
     * 
     * @param abiDefinition abiDefinition
     * @return
     */
    public static List<String> getFuncOutputType(AbiDefinition abiDefinition) {
        List<String> outputList = new ArrayList<>();
        List<NamedType> outputs = abiDefinition.getOutputs();
        for (NamedType output : outputs) {
            outputList.add(output.getType());
        }
        return outputList;
    }

    /**
     * input parameter format.
     * 
     * @param funcInputTypes list
     * @param params list
     * @return
     */
    public static List<Type> inputFormat(List<String> funcInputTypes, List<Object> params)
            throws BaseException {
        List<Type> finalInputs = new ArrayList<>();
        for (int i = 0; i < funcInputTypes.size(); i++) {
            Class<? extends Type> inputType = null;
            Object input = null;
            if (funcInputTypes.get(i).indexOf("[") != -1
                    && funcInputTypes.get(i).indexOf("]") != -1) {
                List<Object> arrList =
                        new ArrayList<>(Arrays.asList(params.get(i).toString().split(",")));
                List<Type> arrParams = new ArrayList<>();
                for (int j = 0; j < arrList.size(); j++) {
                    inputType = (Class<? extends Type>) AbiTypes.getType(
                            funcInputTypes.get(i).substring(0, funcInputTypes.get(i).indexOf("[")));
                    input = ContractTypeUtil.parseByType(
                            funcInputTypes.get(i).substring(0, funcInputTypes.get(i).indexOf("[")),
                            arrList.get(j).toString());
                    arrParams.add(ContractTypeUtil.generateClassFromInput(input.toString(), inputType));
                }
                finalInputs.add(new DynamicArray<>(arrParams));
            } else {
                inputType = (Class<? extends Type>) AbiTypes.getType(funcInputTypes.get(i));
                input = ContractTypeUtil.parseByType(funcInputTypes.get(i),
                        params.get(i).toString());
                finalInputs.add(ContractTypeUtil.generateClassFromInput(input.toString(), inputType));
            }
        }
        return finalInputs;
    }

    /**
     * output parameter format.
     * 
     * @param funOutputTypes list
     * @return
     */
    public static List<TypeReference<?>> outputFormat(List<String> funOutputTypes)
            throws BaseException {
        List<TypeReference<?>> finalOutputs = new ArrayList<>();
        for (int i = 0; i < funOutputTypes.size(); i++) {
            Class<? extends Type> outputType = null;
            TypeReference<?> typeReference = null;
            if (funOutputTypes.get(i).indexOf("[") != -1
                    && funOutputTypes.get(i).indexOf("]") != -1) {
                typeReference = ContractTypeUtil.getArrayType(
                        funOutputTypes.get(i).substring(0, funOutputTypes.get(i).indexOf("[")));
            } else {
                outputType = (Class<? extends Type>) AbiTypes.getType(funOutputTypes.get(i));
                typeReference = TypeReference.create(outputType);
            }
            finalOutputs.add(typeReference);
        }
        return finalOutputs;
    }

    /**
     * ethCall Result Parse.
     * 
     * @param funOutputTypes list
     * @param typeList list
     * @return
     */
    public static Object callResultParse(List<String> funOutputTypes, List<Type> typeList)
            throws TransactionAssembleException, BaseException {
        if (funOutputTypes.size() == typeList.size()) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < funOutputTypes.size(); i++) {
                Class<? extends Type> outputType = null;
                Object value = null;
                if (funOutputTypes.get(i).indexOf("[") != -1
                        && funOutputTypes.get(i).indexOf("]") != -1) {
                    List<Object> values = new ArrayList<>();
                    List<Type> results = (List<Type>) typeList.get(i).getValue();
                    for (int j = 0; j < results.size(); j++) {
                        outputType = (Class<? extends Type>) AbiTypes.getType(funOutputTypes.get(i).substring(0,
                                funOutputTypes.get(i).indexOf("[")));
                        value = ContractTypeUtil.decodeResult(results.get(j), outputType);
                        values.add(value);
                    }
                    result.add(values);
                } else {
                    outputType = (Class<? extends Type>) AbiTypes.getType(funOutputTypes.get(i));
                    value = ContractTypeUtil.decodeResult(typeList.get(i), outputType);
                    result.add(value);
                }
            }
            return JSON.parse(JSON.toJSONString(result));
        }
        throw new TransactionAssembleException("output parameter not match");
    }
    
    /**
     * receiptParse.
     * 
     * @param receipt info
     * @param abiList info
     * @return
     */
    public static Object receiptParse(TransactionReceipt receipt, List<AbiDefinition> abiList)
            throws BaseException, TransactionAssembleException, BaseException {
        Map<String, Object> resultMap = new HashMap<>();
        List<Log> logList = receipt.getLogs();
        for (AbiDefinition abiDefinition : abiList) {
            String eventName = abiDefinition.getName();
            List<String> funcInputTypes = getFuncInputType(abiDefinition);
            List<TypeReference<?>> finalOutputs = outputFormat(funcInputTypes);
            Event event = new Event(eventName,finalOutputs);
            Object result = null;
            for (Log logInfo : logList) {
                EventValues eventValues = Contract.staticExtractEventParameters(event, logInfo);
                if (eventValues != null) {
                    result = callResultParse(funcInputTypes, eventValues.getNonIndexedValues());
                    break;
                }
            }
            if (result != null) {
                resultMap.put(eventName, result);
            }
        }
        return resultMap;
    }

    /**
     * check abi valid
     * @param contractAbi
     */
    public static void checkAbi(String contractAbi) throws TransactionAssembleException {
        try {
            JSONArray abiArr = JSONArray.parseArray(contractAbi);
            for (Object object : abiArr) {
                AbiDefinition a = JSON.parseObject(object.toString(), AbiDefinition.class);
            }
        } catch (JSONException ex) {
            throw new TransactionAssembleException("Contract abi invalid, please check abi");

        }
    }


    public static <T extends Type> T generateClassFromInput(String input, Class<T> type)
            throws BaseException {
        try {
            if (Address.class.isAssignableFrom(type)) {
                return (T) new Address(input);
            } else if (NumericType.class.isAssignableFrom(type)) {
                return (T) encodeNumeric(input, (Class<NumericType>) type);
            } else if (Bool.class.isAssignableFrom(type)) {
                return (T) new Bool(Boolean.valueOf(input));
            } else if (Utf8String.class.isAssignableFrom(type)) {
                return (T) new Utf8String(input);
            } else if (Bytes.class.isAssignableFrom(type)) {
                return (T) encodeBytes(input, (Class<Bytes>) type);
            } else if (DynamicBytes.class.isAssignableFrom(type)) {
                return (T) new DynamicBytes(input.getBytes());
            } else {
                throw new BaseException(201201,
                        String.format("type:%s unsupported encoding", type.getName()));
            }
        } catch (Exception e) {
            throw new BaseException(21200, "contract funcParam is error");
        }
    }

    public static <T extends NumericType> T encodeNumeric(String input, Class<T> type)
            throws BaseException {
        try {
            BigInteger numericValue = new BigInteger(input);
            return type.getConstructor(BigInteger.class).newInstance(numericValue);
        } catch (NoSuchMethodException | SecurityException | InstantiationException
                | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new BaseException(201203,
                    String.format("unable to create instance of type:%s", type.getName()));
        }
    }

    public static <T extends Bytes> T encodeBytes(String input, Class<T> type) throws BaseException {
        try {
            byte[] bytes = Numeric.hexStringToByteArray(input);
            return type.getConstructor(byte[].class).newInstance(bytes);
        } catch (NoSuchMethodException | SecurityException | InstantiationException
                | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new BaseException(201203,
                    String.format("unable to create instance of type:%s", type.getName()));
        }
    }
}
