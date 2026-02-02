package dev.dukedarius.HytaleIndustries.Components.Quarry;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class QuarryProjectileComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<QuarryProjectileComponent> CODEC = BuilderCodec.builder(
            QuarryProjectileComponent.class,
            QuarryProjectileComponent::new
    )
            .append(new KeyedCodec<>("Age", Codec.FLOAT), (c, v) -> c.age = v, c -> c.age)
            .add()
            .append(new KeyedCodec<>("MaxLifetime", Codec.FLOAT), (c, v) -> c.maxLifetime = v, c -> c.maxLifetime)
            .add()
            .build();

    public float age = 0.0f;
    public float maxLifetime = 1f;

    @Override
    public QuarryProjectileComponent clone() {
        try {
            return (QuarryProjectileComponent) super.clone();
        } catch (CloneNotSupportedException e) {
            QuarryProjectileComponent copy = new QuarryProjectileComponent();
            copy.age = this.age;
            copy.maxLifetime = this.maxLifetime;
            return copy;
        }
    }
}
