package com.cavetale.fastleafdecay.decay;

import com.cavetale.fastleafdecay.config.FastLeafDecayConfig;
import com.cavetale.fastleafdecay.config.FastLeafDecayConfigHolder;
import com.cavetale.fastleafdecay.config.WorldFilter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeafDecayExecutorTest {

    private static final Material LEAVES = Material.OAK_LEAVES;
    private static final Material NOT_LEAVES = Material.STONE;

    @Mock
    private PluginManager pluginManager;

    @Mock
    private World world;

    @Mock
    private Block block;

    @Mock
    private Leaves blockData;

    @Captor
    private ArgumentCaptor<LeavesDecayEvent> eventCaptor;

    private FastLeafDecayConfigHolder configHolder;
    private LeafDecayExecutor executor;
    private Location location;

    @BeforeEach
    void setUp() {
        this.configHolder = new FastLeafDecayConfigHolder(FastLeafDecayConfig.defaults());
        this.executor = new LeafDecayExecutor(this.pluginManager, this.configHolder, material -> material == LEAVES);
        this.location = new Location(this.world, 1, 2, 3);

        when(this.world.getBlockAt(this.location)).thenReturn(this.block);
    }

    /**
     * Makes the block at the tested location leaves that are decayed, so the world of
     * the block is needed for the effects.
     */
    private void givenDecayingLeaves(int distance) {
        givenLeaves(false, distance);
        when(this.block.getWorld()).thenReturn(this.world);
    }

    private void givenLeaves(boolean persistent, int distance) {
        when(this.block.getType()).thenReturn(LEAVES);
        when(this.block.getBlockData()).thenReturn(this.blockData);
        when(this.blockData.isPersistent()).thenReturn(persistent);

        if (!persistent) {
            when(this.blockData.getDistance()).thenReturn(distance);
        }
    }

    private void setEffects(boolean spawnParticles, boolean playSound) {
        this.configHolder.set(new FastLeafDecayConfig(WorldFilter.empty(), WorldFilter.empty(),
            FastLeafDecayConfig.DEFAULT_BREAK_DELAY, FastLeafDecayConfig.DEFAULT_DECAY_DELAY, spawnParticles, playSound));
    }

    @Nested
    @DisplayName("the leaves are not decayed")
    class NotDecayed {

        @Test
        void whenTheBlockIsNoLongerLeaves() {
            when(block.getType()).thenReturn(NOT_LEAVES);

            assertFalse(executor.decay(location));
            verifyNothingHappened();
        }

        @Test
        void whenTheLeavesArePersistent() {
            givenLeaves(true, LeafDecayExecutor.MIN_DECAY_DISTANCE);

            assertFalse(executor.decay(location));
            verifyNothingHappened();
        }

        @ParameterizedTest(name = "distance = {0}")
        @ValueSource(ints = {0, 1, LeafDecayExecutor.MIN_DECAY_DISTANCE - 1})
        void whenTheLeavesAreCloserThanTheMinimumDistance(int distance) {
            givenLeaves(false, distance);

            assertFalse(executor.decay(location));
            verifyNothingHappened();
        }

        @Test
        void whenTheDecayEventIsCancelled() {
            givenLeaves(false, LeafDecayExecutor.MIN_DECAY_DISTANCE);
            doAnswer(invocation -> {
                invocation.getArgument(0, LeavesDecayEvent.class).setCancelled(true);
                return null;
            }).when(pluginManager).callEvent(any(LeavesDecayEvent.class));

            assertFalse(executor.decay(location));

            verify(pluginManager).callEvent(any(LeavesDecayEvent.class));
            verifyNoEffectsAndNoBreak();
        }

        private void verifyNothingHappened() {
            verifyNoInteractions(pluginManager);
            verifyNoEffectsAndNoBreak();
        }

        private void verifyNoEffectsAndNoBreak() {
            verify(world, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
            verify(world, never()).playSound(any(Sound.class), anyDouble(), anyDouble(), anyDouble());
            verify(block, never()).breakNaturally();
        }
    }

    @Nested
    @DisplayName("the leaves are decayed")
    class Decayed {

        @ParameterizedTest(name = "distance = {0}")
        @ValueSource(ints = {LeafDecayExecutor.MIN_DECAY_DISTANCE, LeafDecayExecutor.MIN_DECAY_DISTANCE + 1, 63})
        void whenTheLeavesAreAtLeastTheMinimumDistanceAway(int distance) {
            givenDecayingLeaves(distance);

            assertTrue(executor.decay(location));
            verify(block).breakNaturally();
        }

        @Test
        void afterTheDecayEventWasCalledForTheBlock() {
            givenDecayingLeaves(LeafDecayExecutor.MIN_DECAY_DISTANCE);

            assertTrue(executor.decay(location));

            verify(pluginManager).callEvent(eventCaptor.capture());
            assertSame(block, eventCaptor.getValue().getBlock());
            verify(block).breakNaturally();
        }
    }

    @Nested
    @DisplayName("the effects")
    class Effects {

        @ParameterizedTest(name = "SpawnParticles = {0}, PlaySound = {1}")
        @CsvSource({
            "true, true",
            "true, false",
            "false, true",
            "false, false",
        })
        void arePlayedAsConfigured(boolean spawnParticles, boolean playSound) {
            setEffects(spawnParticles, playSound);
            givenDecayingLeaves(LeafDecayExecutor.MIN_DECAY_DISTANCE);

            assertTrue(executor.decay(location));

            verify(world, times(spawnParticles ? 1 : 0)).spawnParticle(eq(Particle.BLOCK), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(blockData));
            verify(world, times(playSound ? 1 : 0)).playSound(any(Sound.class), anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        void spawnParticlesAtTheCenterOfTheBlockWithoutModifyingTheGivenLocation() {
            givenDecayingLeaves(LeafDecayExecutor.MIN_DECAY_DISTANCE);

            assertTrue(executor.decay(location));

            var particleLocation = ArgumentCaptor.forClass(Location.class);
            verify(world).spawnParticle(eq(Particle.BLOCK), particleLocation.capture(), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(blockData));

            assertEquals(new Location(world, 1.5, 2.5, 3.5), particleLocation.getValue());
            assertEquals(new Location(world, 1, 2, 3), location);
        }

        @Test
        void playTheDecaySoundAtThePositionOfTheBlock() {
            givenDecayingLeaves(LeafDecayExecutor.MIN_DECAY_DISTANCE);

            assertTrue(executor.decay(location));

            var sound = ArgumentCaptor.forClass(Sound.class);
            verify(world).playSound(sound.capture(), eq(1.0), eq(2.0), eq(3.0));

            assertEquals(Key.key("block.grass.break"), sound.getValue().name());
            assertEquals(Sound.Source.BLOCK, sound.getValue().source());
        }
    }
}
