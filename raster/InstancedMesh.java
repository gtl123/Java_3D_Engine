package engine.raster;

import engine.entity.Entity;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

public class InstancedMesh extends Mesh {

    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int VECTOR4F_SIZE_BYTES = 4 * FLOAT_SIZE_BYTES;
    private static final int MATRIX_SIZE_BYTES = 4 * VECTOR4F_SIZE_BYTES;
    private static final int MATRIX_SIZE_FLOATS = 16;
    private static final int INSTANCE_CHUNK_SIZE = 1000;

    private int instanceDataVBO;
    private FloatBuffer instanceDataBuffer;

    public InstancedMesh(float[] positions, float[] textCoords, float[] normals, int[] indices, int numInstances) {
        super(positions, textCoords, normals, indices);

        try {
            glBindVertexArray(vaoId);

            instanceDataVBO = glGenBuffers();
            vboIdList.add(instanceDataVBO);
            instanceDataBuffer = MemoryUtil.memAllocFloat(numInstances * MATRIX_SIZE_FLOATS);
            glBindBuffer(GL_ARRAY_BUFFER, instanceDataVBO);

            int start = 3;
            int stride = MATRIX_SIZE_BYTES;

            // Model View Matrix
            for (int i = 0; i < 4; i++) {
                glVertexAttribPointer(start, 4, GL_FLOAT, false, stride, i * VECTOR4F_SIZE_BYTES);
                glVertexAttribDivisor(start, 1);
                glEnableVertexAttribArray(start);
                start++;
            }

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void renderListInstanced(List<Entity> entities, Transformation transformation, Matrix4f viewMatrix) {
        initRender();

        int chunkSize = INSTANCE_CHUNK_SIZE;
        int length = entities.size();
        for (int i = 0; i < length; i += chunkSize) {
            int end = Math.min(length, i + chunkSize);
            List<Entity> subList = entities.subList(i, end);
            renderChunkInstanced(subList, transformation, viewMatrix);
        }

        endRender();
    }

    private void initRender() {
        if (getTexture() != null) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, getTexture().getId());
        }
        glBindVertexArray(vaoId);
    }

    private void endRender() {
        glBindVertexArray(0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void renderChunkInstanced(List<Entity> entities, Transformation transformation, Matrix4f viewMatrix) {
        instanceDataBuffer.clear();
        int i = 0;

        for (Entity entity : entities) {
            Matrix4f modelViewMatrix = transformation.getModelViewMatrix(entity, viewMatrix);
            modelViewMatrix.get(INSTANCE_CHUNK_SIZE * MATRIX_SIZE_FLOATS * i, instanceDataBuffer);
            modelViewMatrix.get(i * MATRIX_SIZE_FLOATS, instanceDataBuffer);
            i++;
        }
        instanceDataBuffer.limit(entities.size() * MATRIX_SIZE_FLOATS);

        glBindBuffer(GL_ARRAY_BUFFER, instanceDataVBO);
        glBufferSubData(GL_ARRAY_BUFFER, 0, instanceDataBuffer);

        glDrawElementsInstanced(GL_TRIANGLES, getVertexCount(), GL_UNSIGNED_INT, 0, entities.size());

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        if (instanceDataBuffer != null) {
            MemoryUtil.memFree(instanceDataBuffer);
        }
    }
}
