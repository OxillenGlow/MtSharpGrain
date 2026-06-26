public class RealWorldAccessor implements WorldAccessor {
    private final com.mtsharpgrain.Worldaccess world; // whatever your actual class is called

    public RealWorldAccessor(com.mtsharpgrain.Worldaccess world) {
        this.world = world;
    }

    @Override
    public int getBlockId(int x, int y, int z) {
        return world.getBlockAt(x, y, z); // or however you actually look it up
    }
}
