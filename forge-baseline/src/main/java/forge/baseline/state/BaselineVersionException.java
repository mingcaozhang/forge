package forge.baseline.state;

/** Thrown when a baseline envelope is stamped with a format version this build cannot read. */
public class BaselineVersionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BaselineVersionException(int version) {
        super("Unsupported baseline format version " + version
                + " (this build speaks version " + BaselineFormat.VERSION + ")");
    }
}
