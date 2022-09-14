package io.github.skippi.hordetest.gravity;

import java.io.Serializable;

class StressData implements Serializable {
  public static byte DEFAULT_VALUE = (byte) 0b11110000; // 1.0f stress, None, base=true

  public static float stress(byte data) {
    final var mask = 0b11111 << 3;
    return ((data & mask) >> 3) / 30f;
  }

  public static byte stress(byte data, float value) {
    final var mask = 0b11111 << 3;
    return (byte) ((data & (~mask)) | ((byte) (value * 30f)) << 3);
  }

  public static StressType type(byte data) {
    final var mask = 0b11 << 1;
    return StressType.values()[(data & mask) >> 1];
  }

  public static byte type(byte data, StressType value) {
    final var mask = 0b11 << 1;
    return (byte) ((data & (~mask)) | value.id() << 1);
  }

  public static boolean baseable(byte data) {
    final var mask = 0b1;
    return (data & mask) == 1;
  }

  public static byte baseable(byte data, boolean value) {
    final var mask = 0b1;
    return (byte) ((data & (~mask)) | (value ? 1 : 0));
  }
}
