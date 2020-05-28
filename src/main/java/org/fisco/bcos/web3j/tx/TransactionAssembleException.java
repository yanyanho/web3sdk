package org.fisco.bcos.web3j.tx;

import java.io.IOException;

public class TransactionAssembleException extends IOException {

    private static final long serialVersionUID = 1L;

    public TransactionAssembleException() {
        super();
    }

    public TransactionAssembleException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransactionAssembleException(String message) {
        super(message);
    }

    public TransactionAssembleException(Throwable cause) {
        super(cause);
    }
}
