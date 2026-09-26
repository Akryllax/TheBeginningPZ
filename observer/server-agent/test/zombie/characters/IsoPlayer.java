package zombie.characters;
public final class IsoPlayer {
    private final Thread owner = Thread.currentThread();
    public float x = 10632.5f;
    public boolean dead;
    public String getUsername() { return "fixture-survivor"; }
    public SurvivorDesc getDescriptor() { return new SurvivorDesc(); }
    public float getX() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Read off game thread");
        return x;
    }
    public float getY() { return 9913.5f; }
    public float getZ() { return 0; }
    public boolean isDead() { return dead; }
}
