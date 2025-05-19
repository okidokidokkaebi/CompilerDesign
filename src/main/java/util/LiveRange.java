package util;



public class LiveRange {
    public int definedLine;
    public int lastUsedLine;

    public LiveRange(int lineNumber, int lineNumber1) {
        definedLine = lineNumber;
        lastUsedLine = lineNumber1;
    }

    public String toString() {
        return "[" + definedLine + ", " + lastUsedLine + "]";
    }
}
