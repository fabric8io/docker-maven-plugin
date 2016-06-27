package io.fabric8.maven.docker.config.handler;

public class ExternalConfigHandlerException extends RuntimeException
{
    private static final long serialVersionUID = -2742743075207582636L;

    public ExternalConfigHandlerException(String message)
    {
        super(message);
    }

    public ExternalConfigHandlerException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
