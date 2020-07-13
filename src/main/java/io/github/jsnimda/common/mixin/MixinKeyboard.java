package io.github.jsnimda.common.mixin;

import io.github.jsnimda.common.input.GlobalInputHandler;
import io.github.jsnimda.common.vanilla.Vanilla;
import net.minecraft.client.Keyboard;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard {
  @Inject(method = "onKey", at = @At(value = "TAIL")) // ref: malilib key hook
  private void onKeyLast(long handle, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
    if (handle == Vanilla.INSTANCE.window().getHandle()) {
      if (Vanilla.INSTANCE.screen() == null) { // non null is handled below
        GlobalInputHandler.INSTANCE.onKey(key, scanCode, action, modifiers);
      }
    }
  }

  @Inject(method = "onKey", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;" +
      "wrapScreenError(Ljava/lang/Runnable;Ljava/lang/String;Ljava/lang/String;)V"), cancellable = true)
  private void onScreenKey(long handle, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
    Screen lastScreen = Vanilla.INSTANCE.screen();
    boolean result = GlobalInputHandler.INSTANCE.onKey(key, scanCode, action, modifiers);
    if (result || lastScreen != Vanilla.INSTANCE.screen()) { // detect gui change, cancel vanilla
      ci.cancel();
    }
  }
}