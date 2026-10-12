package dev.vitrail.sodium;

import dev.vitrail.glsl.EntityVertex;
import dev.vitrail.glsl.MovingBlockVertex;
import dev.vitrail.render.EntityFrame;
import dev.vitrail.render.EntityMesh;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializer;

import org.lwjgl.system.MemoryUtil;

import java.util.Objects;

/**
 * What turns a cuboid Sodium wrote at the game's entity stride into a vertex of the moving block mesh.
 * <p>
 * <strong>The pair {@link EntityMeshSerializer} does not name.</strong> A block entity's model (a bell, a
 * chest, and whatever a mod animates of them) is cuboids, so Sodium writes it at
 * {@code DefaultVertexFormat.ENTITY} and pushes it; when the buffer it lands in was opened for one of the
 * three block pipelines, the buffer is at {@link EntityMesh#movingBlockFormat()} while the mesh carries,
 * and {@code copySlow} asks the registry for ENTITY to that. Left with no pair, the registry generates
 * one, finds no {@code MidTexCoord} in the entity vertex and throws ("Source format is missing element
 * MidTexCoord RG32_FLOAT offset:32"), which took the game down in the shadow pass by any block entity.
 * <p>
 * The four elements of the game's block vertex are copied from where the entity vertex has them, and
 * the four this engine appends are written as {@code BufferBuilderMixin} writes them: the face's own
 * normal (which Sodium already wrote on every corner), the sprite middle and the tangent worked out per
 * quad as {@link EntityMeshSerializer} does, and nought for {@code MidBlock}, the stand-in that mixin
 * writes before a polygon is whole, a model having no block middle of its own.
 */
public final class MovingBlockSerializer implements VertexSerializer {

	private static final VertexFormat WRITTEN_FORMAT = DefaultVertexFormat.ENTITY;

	private static final VertexFormat CARRIED_FORMAT = EntityMesh.movingBlockFormat();

	private static final int WRITTEN = WRITTEN_FORMAT.getVertexSize();

	private static final int CARRIED = CARRIED_FORMAT.getVertexSize();

	/** The game's block vertex, element by element, out of the entity vertex and into the moving one. */
	private static final String[] SHARED = {"Position", "Color", "UV0", "UV2"};

	private static final int[] FROM = new int[SHARED.length];

	private static final int[] INTO = new int[SHARED.length];

	private static final int[] SIZE = new int[SHARED.length];

	static {
		// each element's size from the game's block vertex, where they follow one another in this order
		VertexFormat block = DefaultVertexFormat.BLOCK;
		for (int i = 0; i < SHARED.length; i++) {
			FROM[i] = written(SHARED[i]);
			INTO[i] = carried(SHARED[i]);
			int start = Objects.requireNonNull(block.getElement(SHARED[i])).offset();
			int end = i + 1 < SHARED.length ? Objects.requireNonNull(block.getElement(SHARED[i + 1])).offset()
					: block.getVertexSize();
			SIZE[i] = end - start;
		}
	}

	private static final int POSITION = written("Position");

	private static final int TEX_COORD = written("UV0");

	private static final int WRITTEN_NORMAL = written("Normal");

	private static final int NORMAL = carried(MovingBlockVertex.NORMAL);

	private static final int MID_TEX_COORD = carried(EntityVertex.MID_TEX_COORD);

	private static final int TANGENT = carried(EntityVertex.TANGENT);

	private static final int MID_BLOCK = carried(MovingBlockVertex.MID_BLOCK);

	private MovingBlockSerializer() {
	}

	private static int written(String element) {
		return Objects.requireNonNull(WRITTEN_FORMAT.getElement(element),
				"The game's entity format has no " + element).offset();
	}

	private static int carried(String element) {
		return Objects.requireNonNull(CARRIED_FORMAT.getElement(element),
				"The moving block mesh was built without " + element).offset();
	}

	/** Called beside {@link EntityMeshSerializer#register()}, for the same reason and at the same place. */
	static VertexSerializer create() {
		return new MovingBlockSerializer();
	}

	static VertexFormat carriedFormat() {
		return CARRIED_FORMAT;
	}

	@Override
	public void serialize(long written, long carried, int vertices) {
		int quads = vertices >> 2;
		long from = written;
		long into = carried;
		for (int quad = 0; quad < quads; quad++) {
			long second = from + WRITTEN;
			long third = second + WRITTEN;
			long fourth = third + WRITTEN;

			int normal = MemoryUtil.memGetInt(from + WRITTEN_NORMAL);
			float midU = (MemoryUtil.memGetFloat(from + TEX_COORD)
					+ MemoryUtil.memGetFloat(second + TEX_COORD)
					+ MemoryUtil.memGetFloat(third + TEX_COORD)
					+ MemoryUtil.memGetFloat(fourth + TEX_COORD)) * 0.25F;
			float midV = (MemoryUtil.memGetFloat(from + TEX_COORD + 4L)
					+ MemoryUtil.memGetFloat(second + TEX_COORD + 4L)
					+ MemoryUtil.memGetFloat(third + TEX_COORD + 4L)
					+ MemoryUtil.memGetFloat(fourth + TEX_COORD + 4L)) * 0.25F;
			int tangent = EntityFrame.tangent(EntityFrame.unpack(normal, 0),
					EntityFrame.unpack(normal, 1), EntityFrame.unpack(normal, 2), false,
					MemoryUtil.memGetFloat(from + POSITION),
					MemoryUtil.memGetFloat(from + POSITION + 4L),
					MemoryUtil.memGetFloat(from + POSITION + 8L),
					MemoryUtil.memGetFloat(from + TEX_COORD),
					MemoryUtil.memGetFloat(from + TEX_COORD + 4L),
					MemoryUtil.memGetFloat(second + POSITION),
					MemoryUtil.memGetFloat(second + POSITION + 4L),
					MemoryUtil.memGetFloat(second + POSITION + 8L),
					MemoryUtil.memGetFloat(second + TEX_COORD),
					MemoryUtil.memGetFloat(second + TEX_COORD + 4L),
					MemoryUtil.memGetFloat(third + POSITION),
					MemoryUtil.memGetFloat(third + POSITION + 4L),
					MemoryUtil.memGetFloat(third + POSITION + 8L),
					MemoryUtil.memGetFloat(third + TEX_COORD),
					MemoryUtil.memGetFloat(third + TEX_COORD + 4L));

			for (int corner = 0; corner < 4; corner++) {
				copy(from, into, normal);
				MemoryUtil.memPutFloat(into + MID_TEX_COORD, midU);
				MemoryUtil.memPutFloat(into + MID_TEX_COORD + 4L, midV);
				MemoryUtil.memPutInt(into + TANGENT, tangent);
				from += WRITTEN;
				into += CARRIED;
			}
		}

		for (int vertex = quads << 2; vertex < vertices; vertex++) {
			copy(from, into, MemoryUtil.memGetInt(from + WRITTEN_NORMAL));
			MemoryUtil.memPutFloat(into + MID_TEX_COORD, 0.0F);
			MemoryUtil.memPutFloat(into + MID_TEX_COORD + 4L, 0.0F);
			MemoryUtil.memPutInt(into + TANGENT, EntityFrame.FLAT);
			from += WRITTEN;
			into += CARRIED;
		}
	}

	/** One entity vertex's block elements over one moving block vertex, its normal, and nought for MidBlock. */
	private static void copy(long from, long into, int normal) {
		for (int i = 0; i < SHARED.length; i++) {
			MemoryUtil.memCopy(from + FROM[i], into + INTO[i], SIZE[i]);
		}
		MemoryUtil.memPutInt(into + NORMAL, normal);
		MemoryUtil.memPutInt(into + MID_BLOCK, 0);
	}
}
