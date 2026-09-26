package net.lofers.scenario;

import jassimp.*;

/** Imports only our original asset using the installed game's native importer. No renderer/world. */
public final class DriverAssetFixture {
    public static void main(String[] args) throws Exception {
        Jassimp.setLibraryLoader(new JassimpLibraryLoader() {
            public void loadLibrary() { System.loadLibrary("jassimp64"); }
        });
        AiScene scene=Jassimp.importFile(args[0]);
        if(scene.getNumMeshes()!=1)throw new AssertionError("Expected one mesh");
        AiMesh mesh=scene.getMeshes().get(0);
        if(mesh.getNumFaces()!=204||!mesh.hasNormals()||!mesh.hasTexCoords(0)||mesh.hasBones())
            throw new AssertionError("Missing static mesh attributes");
        for(int i=0;i<mesh.getNumVertices();i++) {
            if(!Float.isFinite(mesh.getPositionX(i))||!Float.isFinite(mesh.getPositionY(i))||!Float.isFinite(mesh.getPositionZ(i)))
                throw new AssertionError("Nonfinite geometry");
        }
        System.out.println("Original driver native import passed: "+mesh.getNumVertices()+" vertices, "+mesh.getNumFaces()+" triangles");
    }
}
