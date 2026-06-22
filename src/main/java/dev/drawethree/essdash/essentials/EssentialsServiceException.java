package dev.drawethree.essdash.essentials;

/** Thrown when an Essentials operation fails; surfaced to the API as a 500/400. */
public class EssentialsServiceException extends RuntimeException {

    public EssentialsServiceException(String message) {
        super(message);
    }

    public EssentialsServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
