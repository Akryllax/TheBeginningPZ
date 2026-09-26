package zombie.iso;
public final class IsoWorld {
    public static final IsoWorld instance = new IsoWorld();
    private final Thread owner = Thread.currentThread();
    private final IsoMetaGrid grid = new IsoMetaGrid();
    public IsoMetaGrid getMetaGrid() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Map read off game thread");
        return grid;
    }
    public static int getWorldVersion() { return 249; }
}
