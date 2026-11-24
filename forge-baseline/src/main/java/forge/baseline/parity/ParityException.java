package forge.baseline.parity;

/**
 * A failure of the parity harness itself (bad input, a malformed deck spec or
 * action file, a game-thread failure, or a stalled match). Thrown by
 * {@link ParityRunner#run(ParityRunner.ParityOptions)} and its helpers; the
 * {@code main} entry point reports it and exits non-zero.
 */
public class ParityException extends RuntimeException {

    public ParityException(String message) {
        super(message);
    }

    public ParityException(String message, Throwable cause) {
        super(message, cause);
    }
}
