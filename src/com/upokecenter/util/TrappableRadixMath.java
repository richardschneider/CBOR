package com.upokecenter.util;
/*
Written in 2014 by Peter O.
Any copyright is dedicated to the Public Domain.
http://creativecommons.org/publicdomain/zero/1.0/
If you like this, you should donate to Peter O.
at: http://peteroupc.github.io/CBOR/
 */

    /**
     * Description of TrappableRadixMath.
     * @param <T> Data type for a numeric value in a particular radix.
     */
  class TrappableRadixMath<T> implements IRadixMath<T>
  {
    private static PrecisionContext GetTrappableContext(PrecisionContext ctx) {
      if (ctx == null) {
        return null;
      }
      if (ctx.getTraps() == 0) {
        return ctx;
      }
      return ctx.WithBlankFlags();
    }

    private T TriggerTraps(T result, PrecisionContext src, PrecisionContext dst) {
      if (src == null || src.getFlags() == 0) {
        return result;
      }
      if (dst != null && dst.getHasFlags()) {
        dst.setFlags(dst.getFlags()|(src.getFlags()));
      }
      int traps = (dst != null) ? dst.getTraps() : 0;
      traps &= src.getFlags();
      if (traps == 0) {
        return result;
      }
      int mutexConditions = traps & (~(
        PrecisionContext.FlagClamped | PrecisionContext.FlagInexact | PrecisionContext.FlagRounded | PrecisionContext.FlagSubnormal));
      if (mutexConditions != 0) {
        for (int i = 0; i < 32; ++i) {
          int flag = mutexConditions & (i << 1);
          if (flag != 0) {
            throw new TrapException(flag, dst, result);
          }
        }
      }
      if ((traps & PrecisionContext.FlagSubnormal) != 0) {
        throw new TrapException(traps & PrecisionContext.FlagSubnormal, dst, result);
      }
      if ((traps & PrecisionContext.FlagInexact) != 0) {
        throw new TrapException(traps & PrecisionContext.FlagInexact, dst, result);
      }
      if ((traps & PrecisionContext.FlagRounded) != 0) {
        throw new TrapException(traps & PrecisionContext.FlagRounded, dst, result);
      }
      if ((traps & PrecisionContext.FlagClamped) != 0) {
        throw new TrapException(traps & PrecisionContext.FlagClamped, dst, result);
      }
      return result;
    }

    private IRadixMath<T> math;

    public TrappableRadixMath (IRadixMath<T> math) {

      this.math = math;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param divisor A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T DivideToIntegerNaturalScale(T thisValue, T divisor, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.DivideToIntegerNaturalScale(thisValue, divisor, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param divisor A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T DivideToIntegerZeroScale(T thisValue, T divisor, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.DivideToIntegerZeroScale(thisValue, divisor, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param value A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Abs(T value, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Abs(value, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param value A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Negate(T value, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Negate(value, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Finds the remainder that results when dividing two T objects.
     * @param thisValue A T object.
     * @param divisor A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return The remainder of the two objects.
     */
    public T Remainder(T thisValue, T divisor, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Remainder(thisValue, divisor, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @return An IRadixMathHelper(T) object.
     */
public IRadixMathHelper<T> GetHelper() {
      return this.math.GetHelper();
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param divisor A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RemainderNear(T thisValue, T divisor, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.RemainderNear(thisValue, divisor, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Pi(PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Pi(tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param pow A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Power(T thisValue, T pow, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Power(thisValue, pow, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Log10(T thisValue, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Log10(thisValue, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Ln(T thisValue, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Ln(thisValue, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Exp(T thisValue, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Exp(thisValue, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T SquareRoot(T thisValue, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.SquareRoot(thisValue, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T NextMinus(T thisValue, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.NextMinus(thisValue, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param otherValue A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T NextToward(T thisValue, T otherValue, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.NextToward(thisValue, otherValue, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T NextPlus(T thisValue, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.NextPlus(thisValue, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param divisor A T object. (3).
     * @param desiredExponent A BigInteger object.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T DivideToExponent(T thisValue, T divisor, BigInteger desiredExponent, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.DivideToExponent(thisValue, divisor, desiredExponent, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Divides two T objects.
     * @param thisValue A T object.
     * @param divisor A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return The quotient of the two objects.
     */
    public T Divide(T thisValue, T divisor, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Divide(thisValue, divisor, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param a A T object. (2).
     * @param b A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T MinMagnitude(T a, T b, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.MinMagnitude(a, b, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param a A T object. (2).
     * @param b A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T MaxMagnitude(T a, T b, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.MaxMagnitude(a, b, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param a A T object. (2).
     * @param b A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Max(T a, T b, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Max(a, b, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param a A T object. (2).
     * @param b A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Min(T a, T b, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Min(a, b, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Multiplies two T objects.
     * @param thisValue A T object.
     * @param other A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return The product of the two objects.
     */
    public T Multiply(T thisValue, T other, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Multiply(thisValue, other, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param multiplicand A T object. (3).
     * @param augend A T object. (4).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T MultiplyAndAdd(T thisValue, T multiplicand, T augend, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.MultiplyAndAdd(thisValue, multiplicand, augend, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToBinaryPrecision(T thisValue, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.RoundToBinaryPrecision(thisValue, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Plus(T thisValue, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Plus(thisValue, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToPrecision(T thisValue, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.RoundToPrecision(thisValue, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param otherValue A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Quantize(T thisValue, T otherValue, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Quantize(thisValue, otherValue, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param expOther A BigInteger object.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToExponentExact(T thisValue, BigInteger expOther, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.RoundToExponentExact(thisValue, expOther, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param expOther A BigInteger object.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToExponentSimple(T thisValue, BigInteger expOther, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.RoundToExponentSimple(thisValue, expOther, ctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param exponent A BigInteger object.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToExponentNoRoundedFlag(T thisValue, BigInteger exponent, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.RoundToExponentNoRoundedFlag(thisValue, exponent, ctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Reduce(T thisValue, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Reduce(thisValue, ctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param other A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Add(T thisValue, T other, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.Add(thisValue, other, tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Compares a T object with this instance.
     * @param thisValue A T object.
     * @param otherValue A T object. (2).
     * @param treatQuietNansAsSignaling A Boolean object.
     * @param ctx A PrecisionContext object.
     * @return Zero if the values are equal; a negative number if this instance
     * is less, or a positive number if this instance is greater.
     */
    public T CompareToWithContext(T thisValue, T otherValue, boolean treatQuietNansAsSignaling, PrecisionContext ctx) {
      PrecisionContext tctx = GetTrappableContext(ctx);
      T result = this.math.CompareToWithContext(
        thisValue,
        otherValue,
        treatQuietNansAsSignaling,
        tctx);
      return this.TriggerTraps(result, tctx, ctx);
    }

    /**
     * Compares a T object with this instance.
     * @param thisValue A T object.
     * @param otherValue A T object. (2).
     * @return Zero if the values are equal; a negative number if this instance
     * is less, or a positive number if this instance is greater.
     */
    public int compareTo(T thisValue, T otherValue) {
      return this.math.compareTo(thisValue, otherValue);
    }
  }
