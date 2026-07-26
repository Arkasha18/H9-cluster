package ipc.fdbus;

/** Raw-message container used by the vehicle's FDBus JNI bridge. */
public class FdbusMsgBuilder {
    protected Object message;
    protected byte[] stream;
    protected String text;

    public FdbusMsgBuilder(Object message) {
        this.message = message;
    }

    public FdbusMsgBuilder(byte[] stream, String text) {
        this.stream = stream;
        this.text = text;
    }

    public boolean build(boolean toText) {
        return true;
    }

    public byte[] toBuffer() {
        return stream;
    }

    @Override
    public String toString() {
        return text;
    }
}
