package dev.slipstream;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
public final class BoardInteraction extends SimpleBlockInteraction {
 public static final BuilderCodec<BoardInteraction> CODEC=BuilderCodec.builder(BoardInteraction.class,BoardInteraction::new,SimpleBlockInteraction.CODEC).build();
 @Override protected void interactWithBlock(World w,CommandBuffer<EntityStore> buffer,InteractionType type,InteractionContext ctx,ItemStack held,Vector3i p,CooldownHandler cooldown) {
  var ref=ctx.getEntity();var player=buffer.getComponent(ref,PlayerRef.getComponentType());if(player==null)return;var target=NativeBlocks.cell(p);
  w.execute(()->{if(ref.isValid())SlipstreamPlugin.instance.nameStation(ref.getStore(),ref,player,target);});
 }
 @Override protected void simulateInteractWithBlock(InteractionType type,InteractionContext ctx,ItemStack held,World w,Vector3i p) {}
}
