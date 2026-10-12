package dev.vitrail.mixin;

import dev.vitrail.render.EntityMesh;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The texture matrix of an enchanted item's render type, while a pack draws it.
 * <p>
 * 26.3 draws an enchanted item, solid entity or armour piece in ONE draw with the glint folded in, and
 * the render type's texture transform is the glint's: its scroll and scale, which the game's item shader
 * applies to the glint coordinate alone. {@code EntityDraw} serves those pipelines with a pack's own
 * item program, which reads that same matrix as {@code gl_TextureMatrix[0]} and moves the ITEM's
 * coordinate with it: off the sprite, into empty atlas, every pixel discarded and the item gone. While
 * the mesh carries (a pack draws the entities or the hand) the matrix is the identity for those four
 * pipelines; otherwise the game's own glint shader draws them and gets the glint's matrix as before.
 */
@Mixin(RenderType.class)
abstract class RenderTypeGlintMixin {

	@Redirect(method = "writeDynamicTransforms",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/rendertype/TextureTransform;createMatrix()Lorg/joml/Matrix4f;"))
	private Matrix4f vitrail$plainTexture(TextureTransform transform) {
		if (EntityMesh.carrying() && vitrail$combinedGlint(((RenderType) (Object) this).pipeline())) {
			return new Matrix4f();
		}
		return transform.createMatrix();
	}

	@SuppressWarnings("ReferenceEquality")
	private static boolean vitrail$combinedGlint(RenderPipeline pipeline) {
		return pipeline == RenderPipelines.ITEM_CUTOUT_GLINT || pipeline == RenderPipelines.ITEM_TRANSLUCENT_GLINT
				|| pipeline == RenderPipelines.ENTITY_SOLID_GLINT
				|| pipeline == RenderPipelines.ARMOR_CUTOUT_NO_CULL_GLINT;
	}
}
