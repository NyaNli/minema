package info.ata4.minecraft.minema.client.modules;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

import info.ata4.minecraft.minema.Minema;
import info.ata4.minecraft.minema.Utils;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;

public class ChunkPreloader extends CaptureModule {

	private Field renderInfosField;
	private Field renderDispatcherField;
	private Field renderChunkField;
	private Field renderViewFrustum;

	@SuppressWarnings("rawtypes")
	@SubscribeEvent
	public void onTick(RenderTickEvent evt) {
		if (evt.phase != Phase.START) {
			return;
		}

		try {
			Iterator iterator = ((List) renderInfosField.get(MC.renderGlobal)).iterator();
			ChunkRenderDispatcher renderDispatcher = (ChunkRenderDispatcher) renderDispatcherField.get(MC.renderGlobal);

			// 250ms timeout
			renderDispatcher.runChunkUploads(System.nanoTime() + 250000000L);

			while (iterator.hasNext()) {
				Object renderInfo = iterator.next();
				RenderChunk rc = (RenderChunk) renderChunkField.get(renderInfo);

				if (rc.needsUpdate()) {
					renderDispatcher.updateChunkNow(rc);
					rc.clearNeedsUpdate();
				}
			}
		} catch (Exception e) {
			Utils.print(I18n.format("minema.error.chunk_preload"), TextFormatting.RED);
			Utils.printError(e);
		}
	}

	@Override
	protected void doEnable() throws Exception {
		renderInfosField = Utils.getField(RenderGlobal.class, "field_72755_R", "renderInfos");
		renderDispatcherField = Utils.getField(RenderGlobal.class, "field_174995_M", "renderDispatcher");
		renderViewFrustum = Utils.getField(RenderGlobal.class, "field_175008_n", "viewFrustum");

		for (Class<?> innerClass : RenderGlobal.class.getDeclaredClasses()) {
			if (innerClass.getName().endsWith("ContainerLocalRenderInformation")) {
				renderChunkField = Utils.getField(innerClass, "field_178036_a", "renderChunk");
				break;
			}
		}

		if (renderInfosField == null || renderDispatcherField == null || renderChunkField == null)
			return;

		if (Minema.instance.getConfig().forcePreloadChunks.get() && renderViewFrustum != null) {
			try {
				ChunkRenderDispatcher chunks = (ChunkRenderDispatcher) renderDispatcherField.get(MC.renderGlobal);
				ViewFrustum frustum = (ViewFrustum) renderViewFrustum.get(MC.renderGlobal);

				for (RenderChunk chunk : frustum.renderChunks) {
					if (chunk.getCompiledChunk() == CompiledChunk.DUMMY) {
						chunks.updateChunkNow(chunk);
					}
				}
			} catch (Exception e) {
				Utils.print("Could not force preload all chunks", TextFormatting.RED);
				Utils.printError(e);
			}
		}

		MinecraftForge.EVENT_BUS.register(this);
	}

	@Override
	protected void doDisable() {
		MinecraftForge.EVENT_BUS.unregister(this);
	}

	@Override
	protected boolean checkEnable() {
		return Minema.instance.getConfig().preloadChunks.get();
	}

}