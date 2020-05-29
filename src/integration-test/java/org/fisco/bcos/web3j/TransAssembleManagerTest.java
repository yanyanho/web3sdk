package org.fisco.bcos.web3j;

import org.fisco.bcos.TestBase;
import org.fisco.bcos.contract.Ok;
import org.fisco.bcos.web3j.crypto.Hash;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.TransactionAssembleManager;
import org.fisco.bcos.web3j.tx.txdecode.BaseException;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

public class TransAssembleManagerTest  extends TestBase {

    private TransactionAssembleManager transactionAssembleManager = new TransactionAssembleManager(web3j,credentials.getAddress());

  String abi ="[{\"constant\":false,\"inputs\":[{\"name\":\"num\",\"type\":\"uint256\"}],\"name\":\"trans\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"name\":\"num\",\"type\":\"uint256\"}],\"name\":\"TransEvent\",\"type\":\"event\"}]";
  String bin  = "608060405234801561001057600080fd5b5060016000800160006101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055506402540be40060006001018190555060028060000160006101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555060006002600101819055506103bf806100c26000396000f30060806040526004361061004c576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff16806366c99139146100515780636d4ce63c1461007e575b600080fd5b34801561005d57600080fd5b5061007c600480360381019080803590602001909291905050506100a9565b005b34801561008a57600080fd5b506100936102e1565b6040518082815260200191505060405180910390f35b8060006001015410806100c757506002600101548160026001015401105b156100d1576102de565b8060006001015403600060010181905550806002600101600082825401925050819055507fc77b710b83d1dc3f3fafeccd08a6c469beb873b2f0975b50d1698e46b3ee5b4c816040518082815260200191505060405180910390a160046080604051908101604052806040805190810160405280600881526020017f323031373034313300000000000000000000000000000000000000000000000081525081526020016000800160009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001600260000160009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001838152509080600181540180825580915050906001820390600052602060002090600402016000909192909190915060008201518160000190805190602001906102419291906102ee565b5060208201518160010160006101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555060408201518160020160006101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff160217905550606082015181600301555050505b50565b6000600260010154905090565b828054600181600116156101000203166002900490600052602060002090601f016020900481019282601f1061032f57805160ff191683800117855561035d565b8280016001018555821561035d579182015b8281111561035c578251825591602001919060010190610341565b5b50905061036a919061036e565b5090565b61039091905b8082111561038c576000816000905550600101610374565b5090565b905600a165627a7a7230582047862dd7accfd1db3db5acd78a1024d17dcfe40e8181e4a11bfb1f31d2a0f8440029";

    @Test
    public void testDeploy() throws IOException, BaseException, InterruptedException, ExecutionException, TimeoutException {
       String s =  transactionAssembleManager.transactionAssembleForDeploy(abi,bin,1 ,null);

      String signstr =  transactionAssembleManager.signMessageByEncryptType(s, credentials.getEcKeyPair(),0 );

       TransactionReceipt t = transactionAssembleManager.sendSignedTransaction(signstr,false);
       String hash = Hash.sha3(signstr);
       assertTrue(hash .equals(t.getTransactionHash()));
        System.out.println(hash);


       TransactionReceipt t1 = transactionAssembleManager.sendSignedTransaction(signstr,true);
        System.out.println(t1.getTransactionHash()+ "  " + t1.getStatus());

    }

    @Test
    public void testSendTransaction() throws Exception {
        Ok ok = Ok.deploy(web3j,credentials,gasPrice,gasLimit).send();
        System.out.println(web3j.getNodeVersion().sendAsync().get().getNodeVersion());
        List ilist = new ArrayList<>();
        ilist.add(5);
        String s =  transactionAssembleManager.transactionAssembleForMethodInvoke(abi,1,ok.getContractAddress(),"trans",ilist);

        String signstr =  transactionAssembleManager.signMessageByEncryptType(s, credentials.getEcKeyPair(),0 );

        TransactionReceipt t1 = transactionAssembleManager.sendSignedTransaction(signstr,true);
        System.out.println(t1.getTransactionHash()+ "  " + t1.getStatus());
    }

    @Test
    public void testSendQueryTransaction() throws Exception {
        Ok ok = Ok.deploy(web3j,credentials,gasPrice,gasLimit).send();
        List ilist = new ArrayList<>();
        ilist.add(5);

        String s =  transactionAssembleManager.transactionAssembleForMethodInvoke(abi,1,ok.getContractAddress(),"trans",ilist);

        String signstr =  transactionAssembleManager.signMessageByEncryptType(s, credentials.getEcKeyPair(),0 );

        TransactionReceipt t1 = transactionAssembleManager.sendSignedTransaction(signstr,true);

        String encodeStr =  transactionAssembleManager.transactionAssembleForMethodInvoke(abi,1,ok.getContractAddress(),"get",null);

        Object s1 =  transactionAssembleManager.sendQueryTransaction(encodeStr,ok.getContractAddress(),"get",abi);
        assertNotNull(s1);
    }



}
