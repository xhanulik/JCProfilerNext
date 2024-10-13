package jcprofiler.card.driver;

abstract class DataStructure {
    public abstract byte[] pack();
    public abstract void unpack(byte[] buffer);
}
