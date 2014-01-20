package com.upokecenter.util;
/*
Written in 2013 by Peter O.
Any copyright is dedicated to the Public Domain.
http://creativecommons.org/publicdomain/zero/1.0/
If you like this, you should donate to Peter O.
at: http://peteroupc.github.io/CBOR/
 */

    /**
     * Encapsulates radix-independent arithmetic.
     * @param <T> Data type for a numeric value in a particular radix.
     */
  class RadixMath<T> implements IRadixMath<T> {
    private static final int IntegerModeFixedScale = 1;
    private static final int IntegerModeRegular = 0;

    private IRadixMathHelper<T> helper;
    private int thisRadix;
    private int support;

    public RadixMath (IRadixMathHelper<T> helper) {
      this.helper = helper;
      this.support = helper.GetArithmeticSupport();
      this.thisRadix = helper.GetRadix();
    }

    private T ReturnQuietNaN(T thisValue, PrecisionContext ctx) {
      BigInteger mant = (this.helper.GetMantissa(thisValue)).abs();
      boolean mantChanged = false;
      if (mant.signum()!=0 && ctx != null && ctx.getPrecision().signum()!=0) {
        BigInteger limit = this.helper.MultiplyByRadixPower(BigInteger.ONE, FastInteger.FromBig(ctx.getPrecision()));
        if (mant.compareTo(limit) >= 0) {
          mant = mant.remainder(limit);
          mantChanged = true;
        }
      }
      int flags = this.helper.GetFlags(thisValue);
      if (!mantChanged && (flags & BigNumberFlags.FlagQuietNaN) != 0) {
        return thisValue;
      }
      flags &= BigNumberFlags.FlagNegative;
      flags |= BigNumberFlags.FlagQuietNaN;
      return this.helper.CreateNewWithFlags(mant, BigInteger.ZERO, flags);
    }

    private T SquareRootHandleSpecial(T thisValue, PrecisionContext ctx) {
      int thisFlags = this.helper.GetFlags(thisValue);
      if ((thisFlags & BigNumberFlags.FlagSpecial) != 0) {
        if ((thisFlags & BigNumberFlags.FlagSignalingNaN) != 0) {
          return this.SignalingNaNInvalid(thisValue, ctx);
        }
        if ((thisFlags & BigNumberFlags.FlagQuietNaN) != 0) {
          return this.ReturnQuietNaN(thisValue, ctx);
        }
        if ((thisFlags & BigNumberFlags.FlagInfinity) != 0) {
          // Square root of infinity
          if ((thisFlags & BigNumberFlags.FlagNegative) != 0) {
            return this.SignalInvalid(ctx);
          }
          return thisValue;
        }
      }
      int sign = this.helper.GetSign(thisValue);
      if (sign < 0) {
        return this.SignalInvalid(ctx);
      }
      return null;
    }

    private T DivisionHandleSpecial(T thisValue, T other, PrecisionContext ctx) {
      int thisFlags = this.helper.GetFlags(thisValue);
      int otherFlags = this.helper.GetFlags(other);
      if (((thisFlags | otherFlags) & BigNumberFlags.FlagSpecial) != 0) {
        T result = this.HandleNotANumber(thisValue, other, ctx);
        if ((Object)result != (Object)null) {
          return result;
        }
        if ((thisFlags & BigNumberFlags.FlagInfinity) != 0 && (otherFlags & BigNumberFlags.FlagInfinity) != 0) {
          // Attempt to divide infinity by infinity
          return this.SignalInvalid(ctx);
        }
        if ((thisFlags & BigNumberFlags.FlagInfinity) != 0) {
          return this.EnsureSign(thisValue, ((thisFlags ^ otherFlags) & BigNumberFlags.FlagNegative) != 0);
        }
        if ((otherFlags & BigNumberFlags.FlagInfinity) != 0) {
          // Divisor is infinity, so result will be epsilon
          if (ctx != null && ctx.getHasExponentRange() && ctx.getPrecision().signum() > 0) {
            if (ctx.getHasFlags()) {
              ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagClamped));
            }
            BigInteger bigexp = ctx.getEMin();
            BigInteger bigprec = ctx.getPrecision();
            bigexp=bigexp.subtract(bigprec);
            bigexp=bigexp.add(BigInteger.ONE);
            thisFlags = (thisFlags ^ otherFlags) & BigNumberFlags.FlagNegative;
            return this.helper.CreateNewWithFlags(BigInteger.ZERO, bigexp, thisFlags);
          }
          thisFlags = (thisFlags ^ otherFlags) & BigNumberFlags.FlagNegative;
          return this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, thisFlags), ctx);
        }
      }
      return null;
    }

    private T RemainderHandleSpecial(T thisValue, T other, PrecisionContext ctx) {
      int thisFlags = this.helper.GetFlags(thisValue);
      int otherFlags = this.helper.GetFlags(other);
      if (((thisFlags | otherFlags) & BigNumberFlags.FlagSpecial) != 0) {
        T result = this.HandleNotANumber(thisValue, other, ctx);
        if ((Object)result != (Object)null) {
          return result;
        }
        if ((thisFlags & BigNumberFlags.FlagInfinity) != 0) {
          return this.SignalInvalid(ctx);
        }
        if ((otherFlags & BigNumberFlags.FlagInfinity) != 0) {
          return this.RoundToPrecision(thisValue, ctx);
        }
      }
      if (this.helper.GetMantissa(other).signum()==0) {
        return this.SignalInvalid(ctx);
      }
      return null;
    }

    private T MinMaxHandleSpecial(T thisValue, T otherValue, PrecisionContext ctx, boolean isMinOp, boolean compareAbs) {
      int thisFlags = this.helper.GetFlags(thisValue);
      int otherFlags = this.helper.GetFlags(otherValue);
      if (((thisFlags | otherFlags) & BigNumberFlags.FlagSpecial) != 0) {
        // Check this value then the other value for signaling NaN
        if ((this.helper.GetFlags(thisValue) & BigNumberFlags.FlagSignalingNaN) != 0) {
          return this.SignalingNaNInvalid(thisValue, ctx);
        }
        if ((this.helper.GetFlags(otherValue) & BigNumberFlags.FlagSignalingNaN) != 0) {
          return this.SignalingNaNInvalid(otherValue, ctx);
        }
        // Check this value then the other value for quiet NaN
        if ((this.helper.GetFlags(thisValue) & BigNumberFlags.FlagQuietNaN) != 0) {
          if ((this.helper.GetFlags(otherValue) & BigNumberFlags.FlagQuietNaN) != 0) {
            // both values are quiet NaN
            return this.ReturnQuietNaN(thisValue, ctx);
          }
          // return "other" for being numeric
          return this.RoundToPrecision(otherValue, ctx);
        }
        if ((this.helper.GetFlags(otherValue) & BigNumberFlags.FlagQuietNaN) != 0) {
          // At this point, "thisValue" can't be NaN,
          // return "thisValue" for being numeric
          return this.RoundToPrecision(thisValue, ctx);
        }
        if ((thisFlags & BigNumberFlags.FlagInfinity) != 0) {
          if (compareAbs && (otherFlags & BigNumberFlags.FlagInfinity) == 0) {
            // treat this as larger
            return isMinOp ? this.RoundToPrecision(otherValue, ctx) : thisValue;
          }
          // This value is infinity
          if (isMinOp) {
            // if negative, will be less than every other number
            return ((thisFlags & BigNumberFlags.FlagNegative) != 0) ? thisValue : this.RoundToPrecision(otherValue, ctx);
            // if positive, will be greater
          } else {
            // if positive, will be greater than every other number
            return ((thisFlags & BigNumberFlags.FlagNegative) == 0) ? thisValue : this.RoundToPrecision(otherValue, ctx);
          }
        }
        if ((otherFlags & BigNumberFlags.FlagInfinity) != 0) {
          if (compareAbs) {
            // treat this as larger (the first value
            // won't be infinity at this point
            return isMinOp ? this.RoundToPrecision(thisValue, ctx) : otherValue;
          }
          if (isMinOp) {
            return ((otherFlags & BigNumberFlags.FlagNegative) == 0) ? this.RoundToPrecision(thisValue, ctx) : otherValue;
          } else {
            return ((otherFlags & BigNumberFlags.FlagNegative) != 0) ? this.RoundToPrecision(thisValue, ctx) : otherValue;
          }
        }
      }
      return null;
    }

    private T HandleNotANumber(T thisValue, T other, PrecisionContext ctx) {
      int thisFlags = this.helper.GetFlags(thisValue);
      int otherFlags = this.helper.GetFlags(other);
      // Check this value then the other value for signaling NaN
      if ((thisFlags & BigNumberFlags.FlagSignalingNaN) != 0) {
        return this.SignalingNaNInvalid(thisValue, ctx);
      }
      if ((otherFlags & BigNumberFlags.FlagSignalingNaN) != 0) {
        return this.SignalingNaNInvalid(other, ctx);
      }
      // Check this value then the other value for quiet NaN
      if ((thisFlags & BigNumberFlags.FlagQuietNaN) != 0) {
        return this.ReturnQuietNaN(thisValue, ctx);
      }
      if ((otherFlags & BigNumberFlags.FlagQuietNaN) != 0) {
        return this.ReturnQuietNaN(other, ctx);
      }
      return null;
    }

    private T MultiplyAddHandleSpecial(T op1, T op2, T op3, PrecisionContext ctx) {
      int op1Flags = this.helper.GetFlags(op1);
      // Check operands in order for signaling NaN
      if ((op1Flags & BigNumberFlags.FlagSignalingNaN) != 0) {
        return this.SignalingNaNInvalid(op1, ctx);
      }
      int op2Flags = this.helper.GetFlags(op2);
      if ((op2Flags & BigNumberFlags.FlagSignalingNaN) != 0) {
        return this.SignalingNaNInvalid(op2, ctx);
      }
      int op3Flags = this.helper.GetFlags(op3);
      if ((op3Flags & BigNumberFlags.FlagSignalingNaN) != 0) {
        return this.SignalingNaNInvalid(op3, ctx);
      }
      // Check operands in order for quiet NaN
      if ((op1Flags & BigNumberFlags.FlagQuietNaN) != 0) {
        return this.ReturnQuietNaN(op1, ctx);
      }
      if ((op2Flags & BigNumberFlags.FlagQuietNaN) != 0) {
        return this.ReturnQuietNaN(op2, ctx);
      }
      // Check multiplying infinity by 0 (important to check
      // now before checking third operand for quiet NaN because
      // this signals invalid operation and the operation starts
      // with multiplying only the first two operands)
      if ((op1Flags & BigNumberFlags.FlagInfinity) != 0) {
        // Attempt to multiply infinity by 0
        if ((op2Flags & BigNumberFlags.FlagSpecial) == 0 && this.helper.GetMantissa(op2).signum()==0) {
          return this.SignalInvalid(ctx);
        }
      }
      if ((op2Flags & BigNumberFlags.FlagInfinity) != 0) {
        // Attempt to multiply infinity by 0
        if ((op1Flags & BigNumberFlags.FlagSpecial) == 0 && this.helper.GetMantissa(op1).signum()==0) {
          return this.SignalInvalid(ctx);
        }
      }
      // Now check third operand for quiet NaN
      if ((op3Flags & BigNumberFlags.FlagQuietNaN) != 0) {
        return this.ReturnQuietNaN(op3, ctx);
      }
      return null;
    }

    private T ValueOf(int value, PrecisionContext ctx) {
      if (ctx == null || !ctx.getHasExponentRange() || ctx.ExponentWithinRange(BigInteger.ZERO)) {
        return this.helper.ValueOf(value);
      }
      return this.RoundToPrecision(this.helper.ValueOf(value), ctx);
    }

    private int CompareToHandleSpecialReturnInt(T thisValue, T other) {
      int thisFlags = this.helper.GetFlags(thisValue);
      int otherFlags = this.helper.GetFlags(other);
      if (((thisFlags | otherFlags) & BigNumberFlags.FlagSpecial) != 0) {
        // Assumes that neither operand is NaN

        if ((thisFlags & BigNumberFlags.FlagInfinity) != 0) {
          // thisValue is infinity
          if ((thisFlags & (BigNumberFlags.FlagInfinity | BigNumberFlags.FlagNegative)) == (otherFlags & (BigNumberFlags.FlagInfinity | BigNumberFlags.FlagNegative))) {
            return 0;
          }
          return ((thisFlags & BigNumberFlags.FlagNegative) == 0) ? 1 : -1;
        }
        if ((otherFlags & BigNumberFlags.FlagInfinity) != 0) {
          // the other value is infinity
          if ((thisFlags & (BigNumberFlags.FlagInfinity | BigNumberFlags.FlagNegative)) == (otherFlags & (BigNumberFlags.FlagInfinity | BigNumberFlags.FlagNegative))) {
            return 0;
          }
          return ((otherFlags & BigNumberFlags.FlagNegative) == 0) ? -1 : 1;
        }
      }
      return 2;
    }

    private T CompareToHandleSpecial(T thisValue, T other, boolean treatQuietNansAsSignaling, PrecisionContext ctx) {
      int thisFlags = this.helper.GetFlags(thisValue);
      int otherFlags = this.helper.GetFlags(other);
      if (((thisFlags | otherFlags) & BigNumberFlags.FlagSpecial) != 0) {
        // Check this value then the other value for signaling NaN
        if ((this.helper.GetFlags(thisValue) & BigNumberFlags.FlagSignalingNaN) != 0) {
          return this.SignalingNaNInvalid(thisValue, ctx);
        }
        if ((this.helper.GetFlags(other) & BigNumberFlags.FlagSignalingNaN) != 0) {
          return this.SignalingNaNInvalid(other, ctx);
        }
        if (treatQuietNansAsSignaling) {
          if ((this.helper.GetFlags(thisValue) & BigNumberFlags.FlagQuietNaN) != 0) {
            return this.SignalingNaNInvalid(thisValue, ctx);
          }
          if ((this.helper.GetFlags(other) & BigNumberFlags.FlagQuietNaN) != 0) {
            return this.SignalingNaNInvalid(other, ctx);
          }
        } else {
          // Check this value then the other value for quiet NaN
          if ((this.helper.GetFlags(thisValue) & BigNumberFlags.FlagQuietNaN) != 0) {
            return this.ReturnQuietNaN(thisValue, ctx);
          }
          if ((this.helper.GetFlags(other) & BigNumberFlags.FlagQuietNaN) != 0) {
            return this.ReturnQuietNaN(other, ctx);
          }
        }
        if ((thisFlags & BigNumberFlags.FlagInfinity) != 0) {
          // thisValue is infinity
          if ((thisFlags & (BigNumberFlags.FlagInfinity | BigNumberFlags.FlagNegative)) == (otherFlags & (BigNumberFlags.FlagInfinity | BigNumberFlags.FlagNegative))) {
            return this.ValueOf(0, null);
          }
          return ((thisFlags & BigNumberFlags.FlagNegative) == 0) ? this.ValueOf(1, null) : this.ValueOf(-1, null);
        }
        if ((otherFlags & BigNumberFlags.FlagInfinity) != 0) {
          // the other value is infinity
          if ((thisFlags & (BigNumberFlags.FlagInfinity | BigNumberFlags.FlagNegative)) == (otherFlags & (BigNumberFlags.FlagInfinity | BigNumberFlags.FlagNegative))) {
            return this.ValueOf(0, null);
          }
          return ((otherFlags & BigNumberFlags.FlagNegative) == 0) ? this.ValueOf(-1, null) : this.ValueOf(1, null);
        }
      }
      return null;
    }

    private T SignalingNaNInvalid(T value, PrecisionContext ctx) {
      if (ctx != null && ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInvalid));
      }
      return this.ReturnQuietNaN(value, ctx);
    }

    private T SignalInvalid(PrecisionContext ctx) {
      if (this.support == BigNumberFlags.FiniteOnly) {
        throw new ArithmeticException("Invalid operation");
      }
      if (ctx != null && ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInvalid));
      }
      return this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, BigNumberFlags.FlagQuietNaN);
    }

    private T SignalInvalidWithMessage(PrecisionContext ctx, String str) {
      if (this.support == BigNumberFlags.FiniteOnly) {
        throw new ArithmeticException(str);
      }
      if (ctx != null && ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInvalid));
      }
      return this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, BigNumberFlags.FlagQuietNaN);
    }

    private T SignalOverflow(boolean neg) {
      return this.support == BigNumberFlags.FiniteOnly ? null : this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, (neg ? BigNumberFlags.FlagNegative : 0) | BigNumberFlags.FlagInfinity);
    }

    private T SignalOverflow2(PrecisionContext pc, boolean neg) {
      if (pc != null && pc.getHasFlags()) {
        pc.setFlags(pc.getFlags()|(PrecisionContext.FlagOverflow | PrecisionContext.FlagInexact | PrecisionContext.FlagRounded));
      }
      if (pc != null && pc.getPrecision().signum()!=0 && pc.getHasExponentRange() && (pc.getRounding() == Rounding.Down || pc.getRounding() == Rounding.ZeroFiveUp || (pc.getRounding() == Rounding.Ceiling && neg) || (pc.getRounding() == Rounding.Floor && !neg))) {
        // Set to the highest possible value for
        // the given precision
        BigInteger overflowMant = BigInteger.ZERO;
        FastInteger fastPrecision = FastInteger.FromBig(pc.getPrecision());
        overflowMant = this.helper.MultiplyByRadixPower(BigInteger.ONE, fastPrecision);
        overflowMant=overflowMant.subtract(BigInteger.ONE);
        FastInteger clamp = FastInteger.FromBig(pc.getEMax()).Increment().Subtract(fastPrecision);
        return this.helper.CreateNewWithFlags(overflowMant, clamp.AsBigInteger(), neg ? BigNumberFlags.FlagNegative : 0);
      }
      return this.SignalOverflow(neg);
    }

    private T SignalDivideByZero(PrecisionContext ctx, boolean neg) {
      if (this.support == BigNumberFlags.FiniteOnly) {
        throw new ArithmeticException("Division by zero");
      }
      if (ctx != null && ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagDivideByZero));
      }
      return this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, BigNumberFlags.FlagInfinity | (neg ? BigNumberFlags.FlagNegative : 0));
    }

    private boolean Round(IShiftAccumulator accum, Rounding rounding, boolean neg, FastInteger fastint) {
      boolean incremented = false;
      int radix = this.thisRadix;
      if (rounding == Rounding.HalfUp) {
        if (accum.getLastDiscardedDigit() >= (radix / 2)) {
          incremented = true;
        }
      } else if (rounding == Rounding.HalfEven) {
        if (accum.getLastDiscardedDigit() >= (radix / 2)) {
          if (accum.getLastDiscardedDigit() > (radix / 2) || accum.getOlderDiscardedDigits() != 0) {
            incremented = true;
          } else if (!fastint.isEvenNumber()) {
            incremented = true;
          }
        }
      } else if (rounding == Rounding.Ceiling) {
        if (!neg && (accum.getLastDiscardedDigit() | accum.getOlderDiscardedDigits()) != 0) {
          incremented = true;
        }
      } else if (rounding == Rounding.Floor) {
        if (neg && (accum.getLastDiscardedDigit() | accum.getOlderDiscardedDigits()) != 0) {
          incremented = true;
        }
      } else if (rounding == Rounding.HalfDown) {
        if (accum.getLastDiscardedDigit() > (radix / 2) || (accum.getLastDiscardedDigit() == (radix / 2) && accum.getOlderDiscardedDigits() != 0)) {
          incremented = true;
        }
      } else if (rounding == Rounding.Up) {
        if ((accum.getLastDiscardedDigit() | accum.getOlderDiscardedDigits()) != 0) {
          incremented = true;
        }
      } else if (rounding == Rounding.ZeroFiveUp) {
        if ((accum.getLastDiscardedDigit() | accum.getOlderDiscardedDigits()) != 0) {
          if (radix == 2) {
            incremented = true;
          } else {
            int lastDigit = FastInteger.Copy(fastint).Remainder(radix).AsInt32();
            if (lastDigit == 0 || lastDigit == (radix / 2)) {
              incremented = true;
            }
          }
        }
      }
      return incremented;
    }

    private boolean RoundGivenDigits(int lastDiscarded, int olderDiscarded, Rounding rounding, boolean neg, BigInteger bigval) {
      boolean incremented = false;
      int radix = this.thisRadix;
      if (rounding == Rounding.HalfUp) {
        if (lastDiscarded >= (radix / 2)) {
          incremented = true;
        }
      } else if (rounding == Rounding.HalfEven) {
        if (lastDiscarded >= (radix / 2)) {
          if (lastDiscarded > (radix / 2) || olderDiscarded != 0) {
            incremented = true;
          } else if (bigval.testBit(0)) {
            incremented = true;
          }
        }
      } else if (rounding == Rounding.Ceiling) {
        if (!neg && (lastDiscarded | olderDiscarded) != 0) {
          incremented = true;
        }
      } else if (rounding == Rounding.Floor) {
        if (neg && (lastDiscarded | olderDiscarded) != 0) {
          incremented = true;
        }
      } else if (rounding == Rounding.HalfDown) {
        if (lastDiscarded > (radix / 2) || (lastDiscarded == (radix / 2) && olderDiscarded != 0)) {
          incremented = true;
        }
      } else if (rounding == Rounding.Up) {
        if ((lastDiscarded | olderDiscarded) != 0) {
          incremented = true;
        }
      } else if (rounding == Rounding.ZeroFiveUp) {
        if ((lastDiscarded | olderDiscarded) != 0) {
          if (radix == 2) {
            incremented = true;
          } else {
            BigInteger bigdigit = bigval.remainder(BigInteger.valueOf(radix));
            int lastDigit = bigdigit.intValue();
            if (lastDigit == 0 || lastDigit == (radix / 2)) {
              incremented = true;
            }
          }
        }
      }
      return incremented;
    }

    private boolean RoundGivenBigInt(IShiftAccumulator accum, Rounding rounding, boolean neg, BigInteger bigval) {
      return this.RoundGivenDigits(accum.getLastDiscardedDigit(), accum.getOlderDiscardedDigits(), rounding, neg, bigval);
    }

    private BigInteger RescaleByExponentDiff(BigInteger mantissa, BigInteger e1, BigInteger e2) {
      if (mantissa.signum() == 0) {
        return BigInteger.ZERO;
      }
      FastInteger diff = FastInteger.FromBig(e1).SubtractBig(e2).Abs();
      return this.helper.MultiplyByRadixPower(mantissa, diff);
    }

    private T EnsureSign(T val, boolean negative) {
      if (val == null) {
        return val;
      }
      int flags = this.helper.GetFlags(val);
      if ((negative && (flags & BigNumberFlags.FlagNegative) == 0) || (!negative && (flags & BigNumberFlags.FlagNegative) != 0)) {
        flags &= ~BigNumberFlags.FlagNegative;
        flags |= negative ? BigNumberFlags.FlagNegative : 0;
        return this.helper.CreateNewWithFlags(this.helper.GetMantissa(val), this.helper.GetExponent(val), flags);
      }
      return val;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param divisor A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T DivideToIntegerNaturalScale(T thisValue, T divisor, PrecisionContext ctx) {
      FastInteger desiredScale = FastInteger.FromBig(this.helper.GetExponent(thisValue)).SubtractBig(this.helper.GetExponent(divisor));
      PrecisionContext ctx2 = PrecisionContext.ForRounding(Rounding.Down).WithBigPrecision(ctx == null ? BigInteger.ZERO : ctx.getPrecision()).WithBlankFlags();
      T ret = this.DivideInternal(thisValue, divisor, ctx2, IntegerModeFixedScale, BigInteger.ZERO);
      if ((ctx2.getFlags() & (PrecisionContext.FlagInvalid | PrecisionContext.FlagDivideByZero)) != 0) {
        if (ctx.getHasFlags()) {
          ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInvalid | PrecisionContext.FlagDivideByZero));
        }
        return ret;
      }
      boolean neg = (this.helper.GetSign(thisValue) < 0) ^ (this.helper.GetSign(divisor) < 0);
      // Now the exponent's sign can only be 0 or positive
      if (this.helper.GetMantissa(ret).signum()==0) {
        // Value is 0, so just change the exponent
        // to the preferred one
        BigInteger dividendExp = this.helper.GetExponent(thisValue);
        BigInteger divisorExp = this.helper.GetExponent(divisor);
        ret = this.helper.CreateNewWithFlags(BigInteger.ZERO, dividendExp.subtract(divisorExp), this.helper.GetFlags(ret));
      } else {
        if (desiredScale.signum() < 0) {
          // Desired scale is negative, shift left
          desiredScale.Negate();
          BigInteger bigmantissa = (this.helper.GetMantissa(ret)).abs();
          bigmantissa = this.helper.MultiplyByRadixPower(bigmantissa, desiredScale);
          BigInteger exponentDivisor = this.helper.GetExponent(divisor);
          ret = this.helper.CreateNewWithFlags(bigmantissa, this.helper.GetExponent(thisValue).subtract(exponentDivisor), this.helper.GetFlags(ret));
        } else if (desiredScale.signum() > 0) {
          // Desired scale is positive, shift away zeros
          // but not after scale is reached
          BigInteger bigmantissa = (this.helper.GetMantissa(ret)).abs();
          FastInteger fastexponent = FastInteger.FromBig(this.helper.GetExponent(ret));
          BigInteger bigradix = BigInteger.valueOf(this.thisRadix);
          while (true) {
            if (desiredScale.compareTo(fastexponent) == 0) {
              break;
            }
            BigInteger bigrem;
            BigInteger bigquo;
{
BigInteger[] divrem=(bigmantissa).divideAndRemainder(bigradix);
bigquo=divrem[0];
bigrem=divrem[1]; }
            if (bigrem.signum()!=0) {
              break;
            }
            bigmantissa = bigquo;
            fastexponent.Increment();
          }
          ret = this.helper.CreateNewWithFlags(bigmantissa, fastexponent.AsBigInteger(), this.helper.GetFlags(ret));
        }
      }
      if (ctx != null) {
        ret = this.RoundToPrecision(ret, ctx);
      }
      ret = this.EnsureSign(ret, neg);
      return ret;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param divisor A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T DivideToIntegerZeroScale(T thisValue, T divisor, PrecisionContext ctx) {
      PrecisionContext ctx2 = PrecisionContext.ForRounding(Rounding.Down).WithBigPrecision(ctx == null ? BigInteger.ZERO : ctx.getPrecision()).WithBlankFlags();
      T ret = this.DivideInternal(thisValue, divisor, ctx2, IntegerModeFixedScale, BigInteger.ZERO);
      if ((ctx2.getFlags() & (PrecisionContext.FlagInvalid | PrecisionContext.FlagDivideByZero)) != 0) {
        if (ctx.getHasFlags()) {
          ctx.setFlags(ctx.getFlags()|(ctx2.getFlags() & (PrecisionContext.FlagInvalid | PrecisionContext.FlagDivideByZero)));
        }
        return ret;
      }
      if (ctx != null) {
        ctx2 = ctx.WithBlankFlags().WithUnlimitedExponents();
        ret = this.RoundToPrecision(ret, ctx2);
        if ((ctx2.getFlags() & PrecisionContext.FlagRounded) != 0) {
          return this.SignalInvalid(ctx);
        }
      }
      return ret;
    }

    /**
     * Not documented yet.
     * @param value A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Abs(T value, PrecisionContext ctx) {
      int flags = this.helper.GetFlags(value);
      if ((flags & BigNumberFlags.FlagSignalingNaN) != 0) {
        return this.SignalingNaNInvalid(value, ctx);
      }
      if ((flags & BigNumberFlags.FlagQuietNaN) != 0) {
        return this.ReturnQuietNaN(value, ctx);
      }
      if ((flags & BigNumberFlags.FlagNegative) != 0) {
        return this.RoundToPrecision(this.helper.CreateNewWithFlags(this.helper.GetMantissa(value), this.helper.GetExponent(value), flags & ~BigNumberFlags.FlagNegative), ctx);
      }
      return this.RoundToPrecision(value, ctx);
    }

    /**
     * Not documented yet.
     * @param value A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Negate(T value, PrecisionContext ctx) {
      int flags = this.helper.GetFlags(value);
      if ((flags & BigNumberFlags.FlagSignalingNaN) != 0) {
        return this.SignalingNaNInvalid(value, ctx);
      }
      if ((flags & BigNumberFlags.FlagQuietNaN) != 0) {
        return this.ReturnQuietNaN(value, ctx);
      }
      BigInteger mant = this.helper.GetMantissa(value);
      if ((flags & BigNumberFlags.FlagInfinity) == 0 && mant.signum()==0) {
        if ((flags & BigNumberFlags.FlagNegative) == 0) {
          // positive 0 minus positive 0 is always positive 0
          return this.RoundToPrecision(this.helper.CreateNewWithFlags(mant, this.helper.GetExponent(value), flags & ~BigNumberFlags.FlagNegative), ctx);
        } else if (ctx != null && ctx.getRounding() == Rounding.Floor) {
          // positive 0 minus negative 0 is negative 0 only if
          // the rounding is Floor
          return this.RoundToPrecision(this.helper.CreateNewWithFlags(mant, this.helper.GetExponent(value), flags | BigNumberFlags.FlagNegative), ctx);
        } else {
          return this.RoundToPrecision(this.helper.CreateNewWithFlags(mant, this.helper.GetExponent(value), flags & ~BigNumberFlags.FlagNegative), ctx);
        }
      }
      flags = flags ^ BigNumberFlags.FlagNegative;
      return this.RoundToPrecision(this.helper.CreateNewWithFlags(mant, this.helper.GetExponent(value), flags), ctx);
    }

    private T AbsRaw(T value) {
      return this.EnsureSign(value, false);
    }

    private boolean IsFinite(T val) {
      return (this.helper.GetFlags(val) & BigNumberFlags.FlagSpecial) == 0;
    }

    private boolean IsNegative(T val) {
      return (this.helper.GetFlags(val) & BigNumberFlags.FlagNegative) != 0;
    }

    private T NegateRaw(T val) {
      if (val == null) {
        return val;
      }
      int sign = this.helper.GetFlags(val) & BigNumberFlags.FlagNegative;
      return this.helper.CreateNewWithFlags(this.helper.GetMantissa(val), this.helper.GetExponent(val), sign == 0 ? BigNumberFlags.FlagNegative : 0);
    }

    private static void TransferFlags(PrecisionContext ctxDst, PrecisionContext ctxSrc) {
      if (ctxDst != null && ctxDst.getHasFlags()) {
        if ((ctxSrc.getFlags() & (PrecisionContext.FlagInvalid | PrecisionContext.FlagDivideByZero)) != 0) {
          ctxDst.setFlags(ctxDst.getFlags()|(ctxSrc.getFlags() & (PrecisionContext.FlagInvalid | PrecisionContext.FlagDivideByZero)));
        } else {
          ctxDst.setFlags(ctxDst.getFlags()|(ctxSrc.getFlags()));
        }
      }
    }

    /**
     * Finds the remainder that results when dividing two T objects.
     * @param thisValue A T object.
     * @param divisor A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return The remainder of the two objects.
     */
    public T Remainder(T thisValue, T divisor, PrecisionContext ctx) {
      PrecisionContext ctx2 = ctx == null ? null : ctx.WithBlankFlags();
      T ret = this.RemainderHandleSpecial(thisValue, divisor, ctx2);
      if ((Object)ret != (Object)null) {
        TransferFlags(ctx, ctx2);
        return ret;
      }
      ret = this.DivideToIntegerZeroScale(thisValue, divisor, ctx2);
      if ((ctx2.getFlags() & PrecisionContext.FlagInvalid) != 0) {
        return this.SignalInvalid(ctx);
      }
      ret = this.Add(thisValue, this.NegateRaw(this.Multiply(ret, divisor, null)), ctx2);
      ret = this.EnsureSign(ret, (this.helper.GetFlags(thisValue) & BigNumberFlags.FlagNegative) != 0);
      TransferFlags(ctx, ctx2);
      return ret;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param divisor A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RemainderNear(T thisValue, T divisor, PrecisionContext ctx) {
      PrecisionContext ctx2 = ctx == null ? PrecisionContext.ForRounding(Rounding.HalfEven).WithBlankFlags() : ctx.WithRounding(Rounding.HalfEven).WithBlankFlags();
      T ret = this.RemainderHandleSpecial(thisValue, divisor, ctx2);
      if ((Object)ret != (Object)null) {
        TransferFlags(ctx, ctx2);
        return ret;
      }
      ret = this.DivideInternal(thisValue, divisor, ctx2, IntegerModeFixedScale, BigInteger.ZERO);
      if ((ctx2.getFlags() & PrecisionContext.FlagInvalid) != 0) {
        return this.SignalInvalid(ctx);
      }
      ctx2 = ctx2.WithBlankFlags();
      ret = this.RoundToPrecision(ret, ctx2);
      if ((ctx2.getFlags() & (PrecisionContext.FlagRounded | PrecisionContext.FlagInvalid)) != 0) {
        return this.SignalInvalid(ctx);
      }
      ctx2 = ctx == null ? PrecisionContext.Unlimited.WithBlankFlags() : ctx.WithBlankFlags();
      T ret2 = this.Add(thisValue, this.NegateRaw(this.Multiply(ret, divisor, null)), ctx2);
      if ((ctx2.getFlags() & PrecisionContext.FlagInvalid) != 0) {
        return this.SignalInvalid(ctx);
      }
      if (this.helper.GetFlags(ret2) == 0 && this.helper.GetMantissa(ret2).signum()==0) {
        ret2 = this.EnsureSign(ret2, (this.helper.GetFlags(thisValue) & BigNumberFlags.FlagNegative) != 0);
      }
      TransferFlags(ctx, ctx2);
      return ret2;
    }

    /**
     * Not documented yet.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Pi(PrecisionContext ctx) {
      if (ctx == null) {
        return this.SignalInvalidWithMessage(ctx, "ctx is null");
      }
      if (ctx.getPrecision().signum()==0) {
        return this.SignalInvalidWithMessage(ctx, "ctx has unlimited precision");
      }
      // Gauss-Legendre algorithm
      T a = this.helper.ValueOf(1);
      PrecisionContext ctxdiv = ctx.WithBigPrecision(ctx.getPrecision().add(BigInteger.TEN)).WithRounding(Rounding.ZeroFiveUp);
      T two = this.helper.ValueOf(2);
      T b = this.Divide(a, this.SquareRoot(two, ctxdiv), ctxdiv);
      T four = this.helper.ValueOf(4);
      T half = ((this.thisRadix & 1) == 0) ? this.helper.CreateNewWithFlags(BigInteger.valueOf(this.thisRadix / 2), BigInteger.ZERO.subtract(BigInteger.ONE), 0) : null;
      T t = this.Divide(a, four, ctxdiv);
      boolean more = true;
      int lastCompare = 0;
      int vacillations = 0;
      T lastGuess = null;
      T guess = null;
      BigInteger powerTwo = BigInteger.ONE;
      while (more) {
        lastGuess = guess;
        T aplusB = this.Add(a, b, null);
        T newA = (half == null) ? this.Divide(aplusB, two, ctxdiv) : this.Multiply(aplusB, half, null);
        T valueAMinusNewA = this.Add(a, this.NegateRaw(newA), null);
        if (!a.equals(b)) {
          T atimesB = this.Multiply(a, b, ctxdiv);
          b = this.SquareRoot(atimesB, ctxdiv);
        }
        a = newA;
        guess = this.Multiply(aplusB, aplusB, null);
        guess = this.Divide(guess, this.Multiply(t, four, null), ctxdiv);
        T newGuess = guess;
        if ((Object)lastGuess != (Object)null) {
          int guessCmp = this.compareTo(lastGuess, newGuess);
          if (guessCmp == 0) {
            more = false;
          } else if ((guessCmp > 0 && lastCompare < 0) || (lastCompare > 0 && guessCmp < 0)) {
            // Guesses are vacillating
            vacillations++;
            if (vacillations > 3 && guessCmp > 0) {
              // When guesses are vacillating, choose the lower guess
              // to reduce rounding errors
              more = false;
            }
          }
          lastCompare = guessCmp;
        }
        if (more) {
          T tmpT = this.Multiply(valueAMinusNewA, valueAMinusNewA, null);
          tmpT = this.Multiply(tmpT, this.helper.CreateNewWithFlags(powerTwo, BigInteger.ZERO, 0), null);
          t = this.Add(t, this.NegateRaw(tmpT), ctxdiv);
          powerTwo=powerTwo.shiftLeft(1);
        }
        guess = newGuess;
      }
      return this.RoundToPrecision(guess, ctx);
    }

    private T LnSeries1(T thisValue, PrecisionContext ctx) {
      boolean more = true;
      int lastCompare = 0;
      int vacillations = 0;
      PrecisionContext ctxdiv = ctx.WithBigPrecision(ctx.getPrecision().add(BigInteger.valueOf(6))).WithRounding(Rounding.ZeroFiveUp);
      T z = this.Add(this.NegateRaw(thisValue), this.helper.ValueOf(1), null);
      T zpow = this.Multiply(z, z, ctxdiv);
      T guess = this.NegateRaw(z);
      T lastGuess = null;
      BigInteger denom = BigInteger.valueOf(2);
      while (more) {
        lastGuess = guess;
        T tmp = this.Divide(zpow, this.helper.CreateNewWithFlags(denom, BigInteger.ZERO, 0), ctxdiv);
        T newGuess = this.Add(guess, this.NegateRaw(tmp), ctxdiv);
        {
          int guessCmp = this.compareTo(lastGuess, newGuess);
          if (guessCmp == 0) {
            more = false;
          } else if ((guessCmp > 0 && lastCompare < 0) || (lastCompare > 0 && guessCmp < 0)) {
            // Guesses are vacillating
            vacillations++;
            if (vacillations > 3 && guessCmp > 0) {
              // When guesses are vacillating, choose the lower guess
              // to reduce rounding errors
              more = false;
            }
          }
          lastCompare = guessCmp;
        }
        guess = newGuess;
        if (more) {
          zpow = this.Multiply(zpow, z, ctxdiv);
          denom=denom.add(BigInteger.ONE);
        }
      }
      return this.RoundToPrecision(guess, ctx);
    }

    /*
    private T LnSeries2(T thisValue, PrecisionContext ctx) {
      boolean more = true;
      int lastCompare = 0;
      int vacillations = 0;
      PrecisionContext ctxdiv = ctx.WithBigPrecision(ctx.getPrecision().add(BigInteger.TEN))
        .WithRounding(Rounding.ZeroFiveUp);
      T z = Add(thisValue, helper.ValueOf(-1), null);
      T zpow = Multiply(z, z, ctxdiv);
      T guess = NegateRaw(z);
      T lastGuess = null;
      boolean negative = false;
      BigInteger denom=BigInteger.valueOf(2);
      while (more) {
        lastGuess = guess;
        T tmp = Divide(zpow, helper.CreateNewWithFlags(denom, BigInteger.ZERO, 0), ctxdiv);
        if (negative) {
 tmp = NegateRaw(tmp);
}
        T newGuess = Add(guess, NegateRaw(tmp), ctxdiv);
        {
          int guessCmp = compareTo(lastGuess, newGuess);
          if (guessCmp == 0) {
            more = false;
          } else if ((guessCmp>0 && lastCompare<0) || (lastCompare>0 && guessCmp<0)) {
            // Guesses are vacillating
            vacillations++;
            if (vacillations>3 && guessCmp>0) {
              // When guesses are vacillating, choose the lower guess
              // to reduce rounding errors
              more = false;
            }
          }
          lastCompare = guessCmp;
        }
        guess = newGuess;
        if (more) {
          zpow = Multiply(zpow, z, ctxdiv);
          negative=!negative;
          denom=denom.add(BigInteger.ONE);
        }
      }
      return RoundToPrecision(guess, ctx);
    }*/

    private T ExpInternal(T thisValue, PrecisionContext ctx) {
      T one = this.helper.ValueOf(1);
      PrecisionContext ctxdiv = ctx.WithBigPrecision(ctx.getPrecision().add(BigInteger.valueOf(6))).WithRounding(Rounding.ZeroFiveUp);
      BigInteger bigintN = BigInteger.valueOf(2);
      BigInteger facto = BigInteger.ONE;
      // Guess starts with 1 + thisValue
      T guess = this.Add(one, thisValue, null);
      T lastGuess = guess;
      T pow = thisValue;
      boolean more = true;
      int lastCompare = 0;
      int vacillations = 0;
      while (more) {
        lastGuess = guess;
        // Iterate by:
        // newGuess = guess + (thisValue^n/factorial(n))
        // (n starts at 2 and increases by 1 after
        // each iteration)
        pow = this.Multiply(pow, thisValue, ctxdiv);
        facto=facto.multiply(bigintN);
        T tmp = this.Divide(pow, this.helper.CreateNewWithFlags(facto, BigInteger.ZERO, 0), ctxdiv);
        T newGuess = this.Add(guess, tmp, ctxdiv);
        {
          int guessCmp = this.compareTo(lastGuess, newGuess);
          if (guessCmp == 0) {
            more = false;
          } else if ((guessCmp > 0 && lastCompare < 0) || (lastCompare > 0 && guessCmp < 0)) {
            // Guesses are vacillating
            vacillations++;
            if (vacillations > 3 && guessCmp > 0) {
              // When guesses are vacillating, choose the lower guess
              // to reduce rounding errors
              more = false;
            }
          }
          lastCompare = guessCmp;
        }
        guess = newGuess;
        if (more) {
          bigintN=bigintN.add(BigInteger.ONE);
        }
      }
      return this.RoundToPrecision(guess, ctx);
    }

    private T PowerIntegral(T thisValue, BigInteger powIntBig, PrecisionContext ctx) {
      int sign = powIntBig.signum();
      T one = this.helper.ValueOf(1);
      if (sign == 0) {
        // however 0 to the power of 0 is undefined
        return this.RoundToPrecision(one, ctx);
      } else if (powIntBig.equals(BigInteger.ONE)) {
        return this.RoundToPrecision(thisValue, ctx);
      } else if (powIntBig.equals(BigInteger.valueOf(2))) {
        return this.Multiply(thisValue, thisValue, ctx);
      } else if (powIntBig.equals(BigInteger.valueOf(3))) {
        return this.Multiply(thisValue, this.Multiply(thisValue, thisValue, null), ctx);
      }
      boolean retvalNeg = this.IsNegative(thisValue) && powIntBig.testBit(0);
      FastInteger error = this.helper.CreateShiftAccumulator((powIntBig).abs()).GetDigitLength();
      error.AddInt(6);
      BigInteger bigError = error.AsBigInteger();
      PrecisionContext ctxdiv = ctx.WithBigPrecision(ctx.getPrecision().add(bigError)).WithRounding(Rounding.ZeroFiveUp).WithBlankFlags();
      if (sign < 0) {
        // Use the reciprocal for negative powers
        thisValue = this.Divide(one, thisValue, ctxdiv);
        powIntBig=powIntBig.negate();
      }
      T r = one;
      while (powIntBig.signum()!=0) {
        if (powIntBig.testBit(0)) {
          r = this.Multiply(r, thisValue, ctxdiv);
          // System.out.println(r);
          if ((ctxdiv.getFlags() & PrecisionContext.FlagOverflow) != 0) {
            return this.SignalOverflow2(ctx, retvalNeg);
          }
        }
        powIntBig=powIntBig.shiftRight(1);
        if (powIntBig.signum()!=0) {
          ctxdiv.setFlags(0);
          T tmp = this.Multiply(thisValue, thisValue, ctxdiv);
          if ((ctxdiv.getFlags() & PrecisionContext.FlagOverflow) != 0) {
            // Avoid multiplying too huge numbers with
            // limited exponent range
            return this.SignalOverflow2(ctx, retvalNeg);
          }
          thisValue = tmp;
        }
      }
      return this.RoundToPrecision(r, ctx);
    }

    private T ExtendPrecision(T thisValue, PrecisionContext ctx) {
      if (ctx == null || ctx.getPrecision().signum()==0) {
        return this.RoundToPrecision(thisValue, ctx);
      }
      BigInteger mant = (this.helper.GetMantissa(thisValue)).abs();
      FastInteger digits = this.helper.CreateShiftAccumulator(mant).GetDigitLength();
      FastInteger fastPrecision = FastInteger.FromBig(ctx.getPrecision());
      BigInteger exponent = this.helper.GetExponent(thisValue);
      if (digits.compareTo(fastPrecision) < 0) {
        fastPrecision.Subtract(digits);
        mant = this.helper.MultiplyByRadixPower(mant, fastPrecision);
        BigInteger bigPrec = fastPrecision.AsBigInteger();
        exponent=exponent.subtract(bigPrec);
      }
      if (ctx != null && ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagRounded));
        ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInexact));
      }
      return this.RoundToPrecision(this.helper.CreateNewWithFlags(mant, exponent, 0), ctx);
    }

    private boolean IsWithinExponentRangeForPow(T thisValue, PrecisionContext ctx) {
      if (ctx == null || !ctx.getHasExponentRange()) {
        return true;
      }
      FastInteger digits = this.helper.CreateShiftAccumulator((this.helper.GetMantissa(thisValue)).abs()).GetDigitLength();
      BigInteger exp = this.helper.GetExponent(thisValue);
      FastInteger fi = FastInteger.FromBig(exp);
      fi.Add(digits);
      fi.Decrement();
      // System.out.println("{0} -> {1}",exp,fi);
      if (fi.signum() < 0) {
        fi.Negate().Divide(2).Negate();
        // System.out.println("{0} II -> {1}",exp,fi);
      }
      exp = fi.AsBigInteger();
      if (exp.compareTo(ctx.getEMin()) < 0 || exp.compareTo(ctx.getEMax()) > 0) {
        return false;
      }
      return true;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param pow A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Power(T thisValue, T pow, PrecisionContext ctx) {
      T ret = this.HandleNotANumber(thisValue, pow, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      int thisSign = this.helper.GetSign(thisValue);
      int powSign = this.helper.GetSign(pow);
      int thisFlags = this.helper.GetFlags(thisValue);
      int powFlags = this.helper.GetFlags(pow);
      if (thisSign == 0 && powSign == 0) {
        // Both operands are zero: invalid
        return this.SignalInvalid(ctx);
      }
      if (thisSign < 0 && (powFlags & BigNumberFlags.FlagInfinity) != 0) {
        // This value is negative and power is infinity: invalid
        return this.SignalInvalid(ctx);
      }
      if (thisSign > 0 && (thisFlags & BigNumberFlags.FlagInfinity) == 0 && (powFlags & BigNumberFlags.FlagInfinity) != 0) {
        // Power is infinity and this value is greater than
        // zero and not infinity
        int cmp = this.compareTo(thisValue, this.helper.ValueOf(1));
        if (cmp < 0) {
          // Value is less than 1
          if (powSign < 0) {
            // Power is negative infinity, return positive infinity
            return this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, BigNumberFlags.FlagInfinity);
          } else {
            // Power is positive infinity, return 0
            return this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, 0), ctx);
          }
        } else if (cmp == 0) {
          // Extend the precision of the ((mantissa instanceof much as possible) ? (much as possible)mantissa : null),
          // in the special case that this value is 1
          return this.ExtendPrecision(this.helper.ValueOf(1), ctx);
        } else {
          // Value is greater than 1
          if (powSign > 0) {
            // Power is positive infinity, return positive infinity
            return pow;
          } else {
            // Power is negative infinity, return 0
            return this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, 0), ctx);
          }
        }
      }
      BigInteger powExponent = this.helper.GetExponent(pow);
      boolean isPowIntegral = powExponent.signum() > 0;
      boolean isPowOdd = false;
      T powInt = null;
      if (!isPowIntegral) {
        powInt = this.Quantize(pow, this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, 0), PrecisionContext.ForRounding(Rounding.Down));
        isPowIntegral = this.compareTo(powInt, pow) == 0;
        isPowOdd = !this.helper.GetMantissa(powInt).testBit(0)==false;
      } else {
        if (powExponent.equals(BigInteger.ZERO)) {
          isPowOdd = !this.helper.GetMantissa(powInt).testBit(0)==false;
        } else if (this.thisRadix % 2 == 0) {
          // Never odd for even radixes
          isPowOdd = false;
        } else {
          powInt = this.Quantize(pow, this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, 0), PrecisionContext.ForRounding(Rounding.Down));
          isPowOdd = !this.helper.GetMantissa(powInt).testBit(0)==false;
        }
      }
      // System.out.println("pow={0} powint={1}",pow,powInt);
      boolean isResultNegative = false;
      if ((thisFlags & BigNumberFlags.FlagNegative) != 0 && (powFlags & BigNumberFlags.FlagInfinity) == 0 && isPowIntegral && isPowOdd) {
        isResultNegative = true;
      }
      if (thisSign == 0 && powSign != 0) {
        int infinityFlags = (powSign < 0) ? BigNumberFlags.FlagInfinity : 0;
        if (isResultNegative) {
          infinityFlags |= BigNumberFlags.FlagNegative;
        }
        thisValue = this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, infinityFlags);
        if ((infinityFlags & BigNumberFlags.FlagInfinity) == 0) {
          thisValue = this.RoundToPrecision(thisValue, ctx);
        }
        return thisValue;
      }
      if ((!isPowIntegral || powSign < 0) && (ctx == null || ctx.getPrecision().signum()==0)) {
        return this.SignalInvalidWithMessage(ctx, "ctx is null or has unlimited precision, and pow's exponent is not an integer or is negative");
      }
      if (thisSign < 0 && !isPowIntegral) {
        return this.SignalInvalid(ctx);
      }
      if ((thisFlags & BigNumberFlags.FlagInfinity) != 0) {
        // This value is infinity
        if (powSign > 0) {
          return this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, (isResultNegative ? BigNumberFlags.FlagNegative : 0) | BigNumberFlags.FlagInfinity), ctx);
        } else if (powSign < 0) {
          return this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, isResultNegative ? BigNumberFlags.FlagNegative : 0), ctx);
        } else {
          return this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ONE, BigInteger.ZERO, 0), ctx);
        }
      }
      if (powSign == 0) {
        return this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ONE, BigInteger.ZERO, 0), ctx);
      }
      if (isPowIntegral) {
        // Special case for 1
        if (this.compareTo(thisValue, this.helper.ValueOf(1)) == 0) {
          if (!this.IsWithinExponentRangeForPow(pow, ctx)) {
            return this.SignalInvalid(ctx);
          }
          return this.helper.ValueOf(1);
        }
        if ((Object)powInt == (Object)null) {
          powInt = this.Quantize(pow, this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, 0), PrecisionContext.ForRounding(Rounding.Down));
        }
        BigInteger signedMant = (this.helper.GetMantissa(powInt)).abs();
        if (powSign < 0) {
          signedMant=signedMant.negate();
        }
        // System.out.println("tv={0} mant={1}",thisValue,signedMant);
        return this.PowerIntegral(thisValue, signedMant, ctx);
      }
      // Special case for 1
      if (this.compareTo(thisValue, this.helper.ValueOf(1)) == 0 && powSign > 0) {
        if (!this.IsWithinExponentRangeForPow(pow, ctx)) {
          return this.SignalInvalid(ctx);
        }
        return this.ExtendPrecision(this.helper.ValueOf(1), ctx);
      }

      PrecisionContext ctxdiv = ctx.WithBigPrecision(ctx.getPrecision().add(BigInteger.TEN)).WithRounding(Rounding.ZeroFiveUp).WithBlankFlags();
      T lnresult = this.Ln(thisValue, ctxdiv);
      lnresult = this.Multiply(lnresult, pow, null);
      ctxdiv = ctx.WithBlankFlags();
      lnresult = this.Exp(lnresult, ctxdiv);
      if ((ctxdiv.getFlags() & (PrecisionContext.FlagClamped | PrecisionContext.FlagOverflow)) != 0) {
        if (!this.IsWithinExponentRangeForPow(thisValue, ctx)) {
          return this.SignalInvalid(ctx);
        }
        if (!this.IsWithinExponentRangeForPow(pow, ctx)) {
          return this.SignalInvalid(ctx);
        }
      }
      if (ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(ctxdiv.getFlags()));
      }
      return lnresult;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Log10(T thisValue, PrecisionContext ctx) {
      if (ctx == null) {
        return this.SignalInvalidWithMessage(ctx, "ctx is null");
      }
      if (ctx.getPrecision().signum()==0) {
        return this.SignalInvalidWithMessage(ctx, "ctx is null or has unlimited precision");
      }
      int flags = this.helper.GetFlags(thisValue);
      if ((flags & BigNumberFlags.FlagSignalingNaN) != 0) {
        // NOTE: Returning a signaling NaN is independent of
        // rounding mode
        return this.SignalingNaNInvalid(thisValue, ctx);
      }
      if ((flags & BigNumberFlags.FlagQuietNaN) != 0) {
        // NOTE: Returning a quiet NaN is independent of
        // rounding mode
        return this.ReturnQuietNaN(thisValue, ctx);
      }
      int sign = this.helper.GetSign(thisValue);
      if (sign < 0) {
        return this.SignalInvalid(ctx);
      }
      if ((flags & BigNumberFlags.FlagInfinity) != 0) {
        return thisValue;
      }
      PrecisionContext ctxCopy = ctx.WithBlankFlags();
      T one = this.helper.ValueOf(1);
      if (sign == 0) {
        // Result is negative infinity if input is 0
        thisValue = this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, BigNumberFlags.FlagNegative | BigNumberFlags.FlagInfinity), ctxCopy);
      } else if (this.compareTo(thisValue, one) == 0) {
        // Result is 0 if input is 1
        thisValue = this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, 0), ctxCopy);
      } else {
        BigInteger exp = this.helper.GetExponent(thisValue);
        BigInteger mant = (this.helper.GetMantissa(thisValue)).abs();
        if (mant.equals(BigInteger.ONE) && this.thisRadix == 10) {
          // Value is 1 and radix is 10, so the result is the exponent
          thisValue = this.RoundToPrecision(this.helper.CreateNewWithFlags(exp, BigInteger.ZERO, exp.signum() < 0 ? BigNumberFlags.FlagNegative : 0), ctxCopy);
        } else {
          BigInteger mantissa = this.helper.GetMantissa(thisValue);
          FastInteger expTmp = FastInteger.FromBig(exp);
          BigInteger tenBig = BigInteger.TEN;
          while (true) {
            BigInteger bigrem;
            BigInteger bigquo;
{
BigInteger[] divrem=(mantissa).divideAndRemainder(tenBig);
bigquo=divrem[0];
bigrem=divrem[1]; }
            if (bigrem.signum()!=0) {
              break;
            }
            mantissa = bigquo;
            expTmp.Increment();
          }
          if (mantissa.compareTo(BigInteger.ONE) == 0) {
            // Value is an integer power of 10
            thisValue = this.RoundToPrecision(this.helper.CreateNewWithFlags(expTmp.AsBigInteger(), BigInteger.ZERO, expTmp.signum() < 0 ? BigNumberFlags.FlagNegative : 0), ctxCopy);
          } else {
            PrecisionContext ctxdiv = ctx.WithBigPrecision(ctx.getPrecision().add(BigInteger.TEN)).WithRounding(Rounding.ZeroFiveUp).WithBlankFlags();
            T ten = this.helper.CreateNewWithFlags(BigInteger.TEN, BigInteger.ZERO, 0);
            T logNatural = this.Ln(thisValue, ctxdiv);
            T logTen = this.Ln(ten, ctxdiv);
            thisValue = this.Divide(logNatural, logTen, ctx);
          }
        }
      }
      if (ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(ctxCopy.getFlags()));
      }
      return thisValue;
    }

    private static BigInteger PowerOfTwo(FastInteger fi) {
      if (fi.signum() <= 0) {
        return BigInteger.ONE;
      }
      if (fi.CanFitInInt32()) {
        int val = fi.AsInt32();
        if (val <= 30) {
          val = 1 << val;
          return BigInteger.valueOf(val);
        }
        return BigInteger.ONE.shiftLeft(val);
      } else {
        BigInteger bi = BigInteger.ONE;
        FastInteger fi2 = FastInteger.Copy(fi);
        while (fi2.signum() > 0) {
          int count = 1000000;
          if (fi2.CompareToInt(1000000) < 0) {
            count = bi.intValue();
          }
          bi=bi.shiftLeft(count);
          fi2.SubtractInt(count);
        }
        return bi;
      }
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Ln(T thisValue, PrecisionContext ctx) {
      if (ctx == null) {
        return this.SignalInvalidWithMessage(ctx, "ctx is null");
      }
      if (ctx.getPrecision().signum()==0) {
        return this.SignalInvalidWithMessage(ctx, "ctx has unlimited precision");
      }
      int flags = this.helper.GetFlags(thisValue);
      if ((flags & BigNumberFlags.FlagSignalingNaN) != 0) {
        // NOTE: Returning a signaling NaN is independent of
        // rounding mode
        return this.SignalingNaNInvalid(thisValue, ctx);
      }
      if ((flags & BigNumberFlags.FlagQuietNaN) != 0) {
        // NOTE: Returning a quiet NaN is independent of
        // rounding mode
        return this.ReturnQuietNaN(thisValue, ctx);
      }
      int sign = this.helper.GetSign(thisValue);
      if (sign < 0) {
        return this.SignalInvalid(ctx);
      }
      if ((flags & BigNumberFlags.FlagInfinity) != 0) {
        return thisValue;
      }
      PrecisionContext ctxCopy = ctx.WithBlankFlags();
      T one = this.helper.ValueOf(1);
      if (sign == 0) {
        return this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, BigNumberFlags.FlagNegative | BigNumberFlags.FlagInfinity);
      } else {
        int cmpOne = this.compareTo(thisValue, one);
        PrecisionContext ctxdiv = null;
        if (cmpOne == 0) {
          // Equal to 1
          thisValue = this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, 0), ctxCopy);
        } else if (cmpOne < 0) {
          // Less than 1
          FastInteger error = this.helper.CreateShiftAccumulator((this.helper.GetMantissa(thisValue)).abs()).GetDigitLength();
          error.AddInt(6);
          BigInteger bigError = error.AsBigInteger();
          ctxdiv = ctx.WithBigPrecision(ctx.getPrecision().add(bigError)).WithRounding(Rounding.ZeroFiveUp).WithBlankFlags();
          T quarter = this.Divide(one, this.helper.ValueOf(4), ctxCopy);
          if (this.compareTo(thisValue, quarter) <= 0) {
            // One quarter or less
            T half = this.Multiply(quarter, this.helper.ValueOf(2), null);
            FastInteger roots = new FastInteger(0);
            // Take square root until this value
            // is one half or more
            while (this.compareTo(thisValue, half) < 0) {
              thisValue = this.SquareRoot(thisValue, ctxdiv.WithUnlimitedExponents());
              roots.Increment();
            }
            thisValue = this.LnSeries1(thisValue, ctxdiv);
            BigInteger bigintRoots = PowerOfTwo(roots);
            // Multiply back 2^X, where X is the number
            // of square root calls
            thisValue = this.Multiply(thisValue, this.helper.CreateNewWithFlags(bigintRoots, BigInteger.ZERO, 0), ctxCopy);
          } else {
            thisValue = this.LnSeries1(thisValue, ctxCopy);
          }
          if (ctx.getHasFlags()) {
            ctxCopy.setFlags(ctxCopy.getFlags()|(PrecisionContext.FlagInexact));
            ctxCopy.setFlags(ctxCopy.getFlags()|(PrecisionContext.FlagRounded));
          }
        } else {
          // Greater than 1
          FastInteger error = this.helper.CreateShiftAccumulator((this.helper.GetMantissa(thisValue)).abs()).GetDigitLength();
          error.AddInt(6);
          BigInteger bigError = error.AsBigInteger();
          ctxdiv = ctx.WithBigPrecision(ctx.getPrecision().add(bigError)).WithRounding(Rounding.ZeroFiveUp).WithBlankFlags();
          T two = this.helper.ValueOf(2);
          if (this.compareTo(thisValue, two) >= 0) {
            FastInteger roots = new FastInteger(0);
            // Take square root until this value
            // is less than 2
            while (this.compareTo(thisValue, two) >= 0) {
              thisValue = this.SquareRoot(thisValue, ctxdiv.WithUnlimitedExponents());
              roots.Increment();
            }
            // Find -Ln(1/thisValue)
            thisValue = this.Divide(one, thisValue, ctxdiv);
            thisValue = this.LnSeries1(thisValue, ctxdiv);
            thisValue = this.NegateRaw(thisValue);
            BigInteger bigintRoots = PowerOfTwo(roots);
            // Multiply back 2^X, where X is the number
            // of square root calls
            thisValue = this.Multiply(thisValue, this.helper.CreateNewWithFlags(bigintRoots, BigInteger.ZERO, 0), ctxCopy);
          } else {
            // Find -Ln(1/thisValue)
            thisValue = this.Divide(one, thisValue, ctxdiv);
            thisValue = this.LnSeries1(thisValue, ctxdiv);
            thisValue = this.NegateRaw(thisValue);
            thisValue = this.RoundToPrecision(thisValue, ctxCopy);
          }
          if (ctx.getHasFlags()) {
            ctxCopy.setFlags(ctxCopy.getFlags()|(PrecisionContext.FlagInexact));
            ctxCopy.setFlags(ctxCopy.getFlags()|(PrecisionContext.FlagRounded));
          }
        }
      }
      if (ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(ctxCopy.getFlags()));
      }
      return thisValue;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Exp(T thisValue, PrecisionContext ctx) {
      if (ctx == null) {
        return this.SignalInvalidWithMessage(ctx, "ctx is null");
      }
      if (ctx.getPrecision().signum()==0) {
        return this.SignalInvalidWithMessage(ctx, "ctx has unlimited precision");
      }
      int flags = this.helper.GetFlags(thisValue);
      if ((flags & BigNumberFlags.FlagSignalingNaN) != 0) {
        // NOTE: Returning a signaling NaN is independent of
        // rounding mode
        return this.SignalingNaNInvalid(thisValue, ctx);
      }
      if ((flags & BigNumberFlags.FlagQuietNaN) != 0) {
        // NOTE: Returning a quiet NaN is independent of
        // rounding mode
        return this.ReturnQuietNaN(thisValue, ctx);
      }
      PrecisionContext ctxCopy = ctx.WithBlankFlags();
      if ((flags & BigNumberFlags.FlagInfinity) != 0) {
        if ((flags & BigNumberFlags.FlagNegative) != 0) {
          T retval = this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, 0), ctxCopy);
          if (ctx.getHasFlags()) {
            ctx.setFlags(ctx.getFlags()|(ctxCopy.getFlags()));
          }
          return retval;
        }
        return thisValue;
      }
      int sign = this.helper.GetSign(thisValue);
      T one = this.helper.ValueOf(1);
      PrecisionContext ctxdiv = ctx.WithBigPrecision(ctx.getPrecision().add(BigInteger.TEN)).WithRounding(Rounding.ZeroFiveUp).WithBlankFlags();
      if (sign == 0) {
        thisValue = this.RoundToPrecision(one, ctxCopy);
      } else if (sign < 0) {
        T val = this.Exp(this.NegateRaw(thisValue), ctxdiv);
        if ((ctxdiv.getFlags() & PrecisionContext.FlagOverflow) != 0 || !this.IsFinite(val)) {
          // Overflow, try again with unlimited exponents
          ctxdiv.setFlags(0);
          ctxdiv = ctxdiv.WithUnlimitedExponents();
          thisValue = this.Exp(this.NegateRaw(thisValue), ctxdiv);
        } else {
          thisValue = val;
        }
        // System.out.println("exp interim {0}",thisValue);
        thisValue = this.Divide(one, thisValue, ctxCopy);
        // System.out.println("exp final {0}",thisValue);
        if (ctx.getHasFlags()) {
          ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInexact | PrecisionContext.FlagRounded));
        }
      } else if (this.compareTo(thisValue, one) < 0) {
        thisValue = this.ExpInternal(thisValue, ctxCopy);
        if (ctx.getHasFlags()) {
          ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInexact | PrecisionContext.FlagRounded));
        }
      } else {
        T intpart = this.Quantize(thisValue, one, PrecisionContext.ForRounding(Rounding.Down));
        T fracpart = this.Add(thisValue, this.NegateRaw(intpart), null);
        fracpart = this.Add(one, this.Divide(fracpart, intpart, ctxdiv), null);
        ctxdiv.setFlags(0);
        thisValue = this.ExpInternal(fracpart, ctxdiv);
        if ((ctxdiv.getFlags() & PrecisionContext.FlagUnderflow) != 0) {
          if (ctx.getHasFlags()) {
            ctx.setFlags(ctx.getFlags()|(ctxdiv.getFlags()));
          }
        }
        if (ctx.getHasFlags()) {
          ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInexact | PrecisionContext.FlagRounded));
        }
        thisValue = this.PowerIntegral(thisValue, this.helper.GetMantissa(intpart), ctxdiv);
        if ((ctxdiv.getFlags() & PrecisionContext.FlagOverflow) != 0) {
          if (ctx.getHasFlags()) {
            ctx.setFlags(ctx.getFlags()|(ctxdiv.getFlags()));
          }
        }
        thisValue = this.RoundToPrecision(thisValue, ctxCopy);
      }
      if (ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(ctxCopy.getFlags()));
      }
      return thisValue;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T SquareRoot(T thisValue, PrecisionContext ctx) {
      if (ctx == null) {
        return this.SignalInvalidWithMessage(ctx, "ctx is null");
      }
      if (ctx.getPrecision().signum()==0) {
        return this.SignalInvalidWithMessage(ctx, "ctx has unlimited precision");
      }
      T ret = this.SquareRootHandleSpecial(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctxtmp = ctx.WithBlankFlags();
      BigInteger currentExp = this.helper.GetExponent(thisValue);
      BigInteger origExp = currentExp;
      BigInteger idealExp;
      idealExp = currentExp;
      idealExp=idealExp.divide(BigInteger.valueOf(2));
      if (currentExp.signum() < 0 && currentExp.testBit(0)) {
        // Round towards negative infinity; BigInteger's
        // division operation rounds towards zero
        idealExp=idealExp.subtract(BigInteger.ONE);
      }
      // System.out.println("curr={0} ideal={1}",currentExp,idealExp);
      if (this.helper.GetSign(thisValue) == 0) {
        ret = this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ZERO, idealExp, this.helper.GetFlags(thisValue)), ctxtmp);
        if (ctx.getHasFlags()) {
          ctx.setFlags(ctx.getFlags()|(ctxtmp.getFlags()));
        }
        return ret;
      }
      BigInteger mantissa = (this.helper.GetMantissa(thisValue)).abs();
      IShiftAccumulator accum = this.helper.CreateShiftAccumulator(mantissa);
      FastInteger digitCount = accum.GetDigitLength();
      FastInteger targetPrecision = FastInteger.FromBig(ctx.getPrecision());
      FastInteger precision = FastInteger.Copy(targetPrecision).Multiply(2).AddInt(2);
      boolean rounded = false;
      boolean inexact = false;
      if (digitCount.compareTo(precision) < 0) {
        FastInteger diff = FastInteger.Copy(precision).Subtract(digitCount);
        // System.out.println(diff);
        if ((!diff.isEvenNumber()) ^ (origExp.testBit(0))) {
          diff.Increment();
        }
        BigInteger bigdiff = diff.AsBigInteger();
        currentExp=currentExp.subtract(bigdiff);
        mantissa = this.helper.MultiplyByRadixPower(mantissa, diff);
      } else if (digitCount.compareTo(precision) < 0) {
        FastInteger diff = FastInteger.Copy(digitCount).Subtract(precision);
        accum.ShiftRight(diff);
        BigInteger bigdiff = diff.AsBigInteger();
        currentExp=currentExp.add(bigdiff);
        mantissa = accum.getShiftedInt();
        rounded = true;
        inexact = (accum.getLastDiscardedDigit() | accum.getOlderDiscardedDigits()) != 0;
      }
      BigInteger[] sr = mantissa.sqrtWithRemainder();
      digitCount = this.helper.CreateShiftAccumulator(sr[0]).GetDigitLength();
      BigInteger squareRootRemainder = sr[1];
      // System.out.println("I {0} -> {1} [target={2}], (zero={3})",
      // mantissa, sr[0], targetPrecision,
      // squareRootRemainder.signum()==0);
      mantissa = sr[0];
      if (squareRootRemainder.signum()!=0) {
        rounded = true;
        inexact = true;
      }
      BigInteger oldexp = currentExp;
      currentExp=currentExp.divide(BigInteger.valueOf(2));
      if (oldexp.signum() < 0 && oldexp.testBit(0)) {
        // Round towards negative infinity; BigInteger's
        // division operation rounds towards zero
        currentExp=currentExp.subtract(BigInteger.ONE);
      }
      T retval = this.helper.CreateNewWithFlags(mantissa, currentExp, 0);
      // System.out.println("idealExp={0}, curr {1} guess={2}",idealExp,currentExp,mantissa);
      retval = this.RoundToPrecisionInternal(retval, 0, inexact ? 1 : 0, null, false, false, ctxtmp);
      currentExp = this.helper.GetExponent(retval);
      // System.out.println("guess I {0} idealExp={1}, curr {2} clamped={3}",guess,
      // idealExp, currentExp, (ctxtmp.getFlags()&PrecisionContext.FlagClamped));
      if ((ctxtmp.getFlags() & PrecisionContext.FlagUnderflow) == 0) {
        int expcmp = currentExp.compareTo(idealExp);
        if (expcmp <= 0 || !this.IsFinite(retval)) {
          retval = this.ReduceToPrecisionAndIdealExponent(retval, ctx.getHasExponentRange() ? ctxtmp : null, inexact ? targetPrecision : null, FastInteger.FromBig(idealExp));
        }
      }
      if (ctx.getHasFlags() && ctx.getClampNormalExponents() && !this.helper.GetExponent(retval).equals(idealExp) && (ctxtmp.getFlags() & PrecisionContext.FlagInexact) == 0) {
        ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagClamped));
      }
      if ((ctxtmp.getFlags() & PrecisionContext.FlagOverflow) != 0) {
        rounded = true;
      }
      // System.out.println("guess II {0}",guess);
      currentExp = this.helper.GetExponent(retval);
      if (rounded) {
        ctxtmp.setFlags(ctxtmp.getFlags()|(PrecisionContext.FlagRounded));
      } else {
        if (currentExp.compareTo(idealExp) > 0) {
          // Greater than the ideal, treat as rounded anyway
          ctxtmp.setFlags(ctxtmp.getFlags()|(PrecisionContext.FlagRounded));
        } else {
          // System.out.println("idealExp={0}, curr {1} (II)",idealExp,currentExp);
          ctxtmp.setFlags(ctxtmp.getFlags()&~(PrecisionContext.FlagRounded));
        }
      }
      if (inexact) {
        ctxtmp.setFlags(ctxtmp.getFlags()|(PrecisionContext.FlagRounded));
        ctxtmp.setFlags(ctxtmp.getFlags()|(PrecisionContext.FlagInexact));
      }
      if (ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(ctxtmp.getFlags()));
      }
      return retval;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T NextMinus(T thisValue, PrecisionContext ctx) {
      if (ctx == null) {
        return this.SignalInvalidWithMessage(ctx, "ctx is null");
      }
      if (ctx.getPrecision().signum()==0) {
        return this.SignalInvalidWithMessage(ctx, "ctx has unlimited precision");
      }
      if (!ctx.getHasExponentRange()) {
        return this.SignalInvalidWithMessage(ctx, "doesn't satisfy ctx.getHasExponentRange()");
      }
      int flags = this.helper.GetFlags(thisValue);
      if ((flags & BigNumberFlags.FlagSignalingNaN) != 0) {
        return this.SignalingNaNInvalid(thisValue, ctx);
      }
      if ((flags & BigNumberFlags.FlagQuietNaN) != 0) {
        return this.ReturnQuietNaN(thisValue, ctx);
      }
      if ((flags & BigNumberFlags.FlagInfinity) != 0) {
        if ((flags & BigNumberFlags.FlagNegative) != 0) {
          return thisValue;
        } else {
          BigInteger bigexp2 = ctx.getEMax();
          BigInteger bigprec = ctx.getPrecision();
          bigexp2=bigexp2.add(BigInteger.ONE);
          bigexp2=bigexp2.subtract(bigprec);
          BigInteger overflowMant = this.helper.MultiplyByRadixPower(BigInteger.ONE, FastInteger.FromBig(ctx.getPrecision()));
          overflowMant=overflowMant.subtract(BigInteger.ONE);
          return this.helper.CreateNewWithFlags(overflowMant, bigexp2, 0);
        }
      }
      FastInteger minexp = FastInteger.FromBig(ctx.getEMin()).SubtractBig(ctx.getPrecision()).Increment();
      FastInteger bigexp = FastInteger.FromBig(this.helper.GetExponent(thisValue));
      if (bigexp.compareTo(minexp) <= 0) {
        // Use a smaller exponent if the input exponent is already
        // very small
        minexp = FastInteger.Copy(bigexp).SubtractInt(2);
      }
      T quantum = this.helper.CreateNewWithFlags(BigInteger.ONE, minexp.AsBigInteger(), BigNumberFlags.FlagNegative);
      PrecisionContext ctx2;
      ctx2 = ctx.WithRounding(Rounding.Floor);
      return this.Add(thisValue, quantum, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param otherValue A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T NextToward(T thisValue, T otherValue, PrecisionContext ctx) {
      if (ctx == null) {
        return this.SignalInvalidWithMessage(ctx, "ctx is null");
      }
      if (ctx.getPrecision().signum()==0) {
        return this.SignalInvalidWithMessage(ctx, "ctx has unlimited precision");
      }
      if (!ctx.getHasExponentRange()) {
        return this.SignalInvalidWithMessage(ctx, "doesn't satisfy ctx.getHasExponentRange()");
      }
      int thisFlags = this.helper.GetFlags(thisValue);
      int otherFlags = this.helper.GetFlags(otherValue);
      if (((thisFlags | otherFlags) & BigNumberFlags.FlagSpecial) != 0) {
        T result = this.HandleNotANumber(thisValue, otherValue, ctx);
        if ((Object)result != (Object)null) {
          return result;
        }
      }
      PrecisionContext ctx2;
      int cmp = this.compareTo(thisValue, otherValue);
      if (cmp == 0) {
        return this.RoundToPrecision(this.EnsureSign(thisValue, (otherFlags & BigNumberFlags.FlagNegative) != 0), ctx.WithNoFlags());
      } else {
        if ((thisFlags & BigNumberFlags.FlagInfinity) != 0) {
          if ((thisFlags & (BigNumberFlags.FlagInfinity | BigNumberFlags.FlagNegative)) == (otherFlags & (BigNumberFlags.FlagInfinity | BigNumberFlags.FlagNegative))) {
            // both values are the same infinity
            return thisValue;
          } else {
            BigInteger bigexp2 = ctx.getEMax();
            BigInteger bigprec = ctx.getPrecision();
            bigexp2=bigexp2.add(BigInteger.ONE);
            bigexp2=bigexp2.subtract(bigprec);
            BigInteger overflowMant = this.helper.MultiplyByRadixPower(BigInteger.ONE, FastInteger.FromBig(ctx.getPrecision()));
            overflowMant=overflowMant.subtract(BigInteger.ONE);
            return this.helper.CreateNewWithFlags(overflowMant, bigexp2, thisFlags & BigNumberFlags.FlagNegative);
          }
        }
        FastInteger minexp = FastInteger.FromBig(ctx.getEMin()).SubtractBig(ctx.getPrecision()).Increment();
        FastInteger bigexp = FastInteger.FromBig(this.helper.GetExponent(thisValue));
        if (bigexp.compareTo(minexp) < 0) {
          // Use a smaller exponent if the input exponent is already
          // very small
          minexp = FastInteger.Copy(bigexp).SubtractInt(2);
        } else {
          // Ensure the exponent is lower than the exponent range
          // (necessary to flag underflow correctly)
          minexp.SubtractInt(2);
        }
        T quantum = this.helper.CreateNewWithFlags(BigInteger.ONE, minexp.AsBigInteger(), (cmp > 0) ? BigNumberFlags.FlagNegative : 0);
        T val = thisValue;
        ctx2 = ctx.WithRounding((cmp > 0) ? Rounding.Floor : Rounding.Ceiling).WithBlankFlags();
        val = this.Add(val, quantum, ctx2);
        if ((ctx2.getFlags() & (PrecisionContext.FlagOverflow | PrecisionContext.FlagUnderflow)) == 0) {
          // Don't set flags except on overflow or underflow
          // TODO: Pending clarification from Mike Cowlishaw,
          // author of the Decimal Arithmetic test cases from
          // speleotrove.com
          ctx2.setFlags(0);
        }
        if ((ctx2.getFlags() & PrecisionContext.FlagUnderflow) != 0) {
          BigInteger bigmant = (this.helper.GetMantissa(val)).abs();
          BigInteger maxmant = this.helper.MultiplyByRadixPower(BigInteger.ONE, FastInteger.FromBig(ctx.getPrecision()).Decrement());
          if (bigmant.compareTo(maxmant) >= 0 || ctx.getPrecision().compareTo(BigInteger.ONE) == 0) {
            // don't treat max-precision results as having underflowed
            ctx2.setFlags(0);
          }
        }
        if (ctx.getHasFlags()) {
          ctx.setFlags(ctx.getFlags()|(ctx2.getFlags()));
        }
        return val;
      }
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T NextPlus(T thisValue, PrecisionContext ctx) {
      if (ctx == null) {
        return this.SignalInvalidWithMessage(ctx, "ctx is null");
      }
      if (ctx.getPrecision().signum()==0) {
        return this.SignalInvalidWithMessage(ctx, "ctx has unlimited precision");
      }
      if (!ctx.getHasExponentRange()) {
        return this.SignalInvalidWithMessage(ctx, "doesn't satisfy ctx.getHasExponentRange()");
      }
      int flags = this.helper.GetFlags(thisValue);
      if ((flags & BigNumberFlags.FlagSignalingNaN) != 0) {
        return this.SignalingNaNInvalid(thisValue, ctx);
      }
      if ((flags & BigNumberFlags.FlagQuietNaN) != 0) {
        return this.ReturnQuietNaN(thisValue, ctx);
      }
      if ((flags & BigNumberFlags.FlagInfinity) != 0) {
        if ((flags & BigNumberFlags.FlagNegative) != 0) {
          BigInteger bigexp2 = ctx.getEMax();
          BigInteger bigprec = ctx.getPrecision();
          bigexp2=bigexp2.add(BigInteger.ONE);
          bigexp2=bigexp2.subtract(bigprec);
          BigInteger overflowMant = this.helper.MultiplyByRadixPower(BigInteger.ONE, FastInteger.FromBig(ctx.getPrecision()));
          overflowMant=overflowMant.subtract(BigInteger.ONE);
          return this.helper.CreateNewWithFlags(overflowMant, bigexp2, BigNumberFlags.FlagNegative);
        } else {
          return thisValue;
        }
      }
      FastInteger minexp = FastInteger.FromBig(ctx.getEMin()).SubtractBig(ctx.getPrecision()).Increment();
      FastInteger bigexp = FastInteger.FromBig(this.helper.GetExponent(thisValue));
      if (bigexp.compareTo(minexp) <= 0) {
        // Use a smaller exponent if the input exponent is already
        // very small
        minexp = FastInteger.Copy(bigexp).SubtractInt(2);
      }
      T quantum = this.helper.CreateNewWithFlags(BigInteger.ONE, minexp.AsBigInteger(), 0);
      PrecisionContext ctx2;
      T val = thisValue;
      ctx2 = ctx.WithRounding(Rounding.Ceiling);
      return this.Add(val, quantum, ctx2);
    }

    /**
     * Divides two T objects.
     * @param thisValue A T object.
     * @param divisor A T object. (2).
     * @param desiredExponent A BigInteger object.
     * @param ctx A PrecisionContext object.
     * @return The quotient of the two objects.
     */
    public T DivideToExponent(T thisValue, T divisor, BigInteger desiredExponent, PrecisionContext ctx) {
      if (ctx != null && !ctx.ExponentWithinRange(desiredExponent)) {
        return this.SignalInvalidWithMessage(ctx, "Exponent not within exponent range: " + desiredExponent.toString());
      }
      PrecisionContext ctx2 = (ctx == null) ? PrecisionContext.ForRounding(Rounding.HalfDown) : ctx.WithUnlimitedExponents().WithPrecision(0);
      T ret = this.DivideInternal(thisValue, divisor, ctx2, IntegerModeFixedScale, desiredExponent);
      if (ctx != null && ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(ctx2.getFlags()));
      }
      return ret;
    }

    /**
     * Divides two T objects.
     * @param thisValue A T object.
     * @param divisor A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return The quotient of the two objects.
     */
    public T Divide(T thisValue, T divisor, PrecisionContext ctx) {
      return this.DivideInternal(thisValue, divisor, ctx, IntegerModeRegular, BigInteger.ZERO);
    }

    private int[] RoundToScaleStatus(BigInteger remainder, BigInteger divisor, PrecisionContext ctx) {
      Rounding rounding = (ctx == null) ? Rounding.HalfEven : ctx.getRounding();
      int lastDiscarded = 0;
      int olderDiscarded = 0;
      if (remainder.signum()!=0) {
        if (rounding == Rounding.HalfDown || rounding == Rounding.HalfUp || rounding == Rounding.HalfEven) {
          BigInteger halfDivisor = divisor.shiftRight(1);
          int cmpHalf = remainder.compareTo(halfDivisor);
          if ((cmpHalf == 0) && divisor.testBit(0)==false) {
            // remainder is exactly half
            lastDiscarded = this.thisRadix / 2;
            olderDiscarded = 0;
          } else if (cmpHalf > 0) {
            // remainder is greater than half
            lastDiscarded = this.thisRadix / 2;
            olderDiscarded = 1;
          } else {
            // remainder is less than half
            lastDiscarded = 0;
            olderDiscarded = 1;
          }
        } else {
          // Rounding mode doesn't care about
          // whether remainder is exactly half
          if (rounding == Rounding.Unnecessary) {
            // Rounding was required
            return null;
          }
          lastDiscarded = 1;
          olderDiscarded = 1;
        }
      }
      return new int[] {
        lastDiscarded,
        olderDiscarded
      };
    }

    private T RoundToScale(
      BigInteger mantissa,
      BigInteger remainder,
      BigInteger divisor,
      BigInteger desiredExponent,
      FastInteger shift,
      boolean neg,
      PrecisionContext ctx) {

      IShiftAccumulator accum;
      Rounding rounding = (ctx == null) ? Rounding.HalfEven : ctx.getRounding();
      int lastDiscarded = 0;
      int olderDiscarded = 0;
      if (remainder.signum()!=0) {
        if (rounding == Rounding.HalfDown || rounding == Rounding.HalfUp || rounding == Rounding.HalfEven) {
          BigInteger halfDivisor = divisor.shiftRight(1);
          int cmpHalf = remainder.compareTo(halfDivisor);
          if ((cmpHalf == 0) && divisor.testBit(0)==false) {
            // remainder is exactly half
            lastDiscarded = this.thisRadix / 2;
            olderDiscarded = 0;
          } else if (cmpHalf > 0) {
            // remainder is greater than half
            lastDiscarded = this.thisRadix / 2;
            olderDiscarded = 1;
          } else {
            // remainder is less than half
            lastDiscarded = 0;
            olderDiscarded = 1;
          }
        } else {
          // Rounding mode doesn't care about
          // whether remainder is exactly half
          if (rounding == Rounding.Unnecessary) {
            return this.SignalInvalidWithMessage(ctx, "Rounding was required");
          }
          lastDiscarded = 1;
          olderDiscarded = 1;
        }
      }
      int flags = 0;
      BigInteger newmantissa = mantissa;
      if (shift.isValueZero()) {
        if ((lastDiscarded | olderDiscarded) != 0) {
          flags |= PrecisionContext.FlagInexact | PrecisionContext.FlagRounded;
          if (rounding == Rounding.Unnecessary) {
            return this.SignalInvalidWithMessage(ctx, "Rounding was required");
          }
          if (this.RoundGivenDigits(lastDiscarded, olderDiscarded, rounding, neg, newmantissa)) {
            newmantissa=newmantissa.add(BigInteger.ONE);
          }
        }
      } else {
        accum = this.helper.CreateShiftAccumulatorWithDigits(mantissa, lastDiscarded, olderDiscarded);
        accum.ShiftRight(shift);
        newmantissa = accum.getShiftedInt();
        if (accum.getDiscardedDigitCount().signum() != 0 || (accum.getLastDiscardedDigit() | accum.getOlderDiscardedDigits()) != 0) {
          if (mantissa.signum()!=0) {
            flags |= PrecisionContext.FlagRounded;
          }
          if ((accum.getLastDiscardedDigit() | accum.getOlderDiscardedDigits()) != 0) {
            flags |= PrecisionContext.FlagInexact | PrecisionContext.FlagRounded;
            if (rounding == Rounding.Unnecessary) {
              return this.SignalInvalidWithMessage(ctx, "Rounding was required");
            }
          }
          if (this.RoundGivenBigInt(accum, rounding, neg, newmantissa)) {
            newmantissa=newmantissa.add(BigInteger.ONE);
          }
        }
      }
      if (ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(flags));
      }
      return this.helper.CreateNewWithFlags(
 newmantissa,
 desiredExponent,
 neg ? BigNumberFlags.FlagNegative : 0);
    }

    private T DivideInternal(T thisValue, T divisor, PrecisionContext ctx, int integerMode, BigInteger desiredExponent) {
      T ret = this.DivisionHandleSpecial(thisValue, divisor, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      int signA = this.helper.GetSign(thisValue);
      int signB = this.helper.GetSign(divisor);
      if (signB == 0) {
        if (signA == 0) {
          return this.SignalInvalid(ctx);
        }
        boolean flagsNeg = ((this.helper.GetFlags(thisValue) & BigNumberFlags.FlagNegative) != 0) ^ ((this.helper.GetFlags(divisor) & BigNumberFlags.FlagNegative) != 0);
        return this.SignalDivideByZero(ctx, flagsNeg);
      }
      int radix = this.thisRadix;
      if (signA == 0) {
        T retval = null;
        if (integerMode == IntegerModeFixedScale) {
          int newflags = (this.helper.GetFlags(thisValue) & BigNumberFlags.FlagNegative) ^ (this.helper.GetFlags(divisor) & BigNumberFlags.FlagNegative);
          retval = this.helper.CreateNewWithFlags(BigInteger.ZERO, desiredExponent, newflags);
        } else {
          BigInteger dividendExp = this.helper.GetExponent(thisValue);
          BigInteger divisorExp = this.helper.GetExponent(divisor);
          int newflags = (this.helper.GetFlags(thisValue) & BigNumberFlags.FlagNegative) ^ (this.helper.GetFlags(divisor) & BigNumberFlags.FlagNegative);
          retval = this.RoundToPrecision(this.helper.CreateNewWithFlags(BigInteger.ZERO, dividendExp.subtract(divisorExp), newflags), ctx);
        }
        return retval;
      } else {
        BigInteger mantissaDividend = (this.helper.GetMantissa(thisValue)).abs();
        BigInteger mantissaDivisor = (this.helper.GetMantissa(divisor)).abs();
        FastInteger expDividend = FastInteger.FromBig(this.helper.GetExponent(thisValue));
        FastInteger expDivisor = FastInteger.FromBig(this.helper.GetExponent(divisor));
        FastInteger expdiff = FastInteger.Copy(expDividend).Subtract(expDivisor);
        FastInteger adjust = new FastInteger(0);
        FastInteger result = new FastInteger(0);
        FastInteger naturalExponent = FastInteger.Copy(expdiff);
        boolean hasPrecision = ctx != null && ctx.getPrecision().signum() != 0;
        boolean resultNeg = (this.helper.GetFlags(thisValue) & BigNumberFlags.FlagNegative) != (this.helper.GetFlags(divisor) & BigNumberFlags.FlagNegative);
        FastInteger fastPrecision = (!hasPrecision) ? new FastInteger(0) : FastInteger.FromBig(ctx.getPrecision());
        FastInteger dividendPrecision = null;
        FastInteger divisorPrecision = null;
        if (integerMode == IntegerModeFixedScale) {
          FastInteger shift;
          BigInteger rem;
          FastInteger fastDesiredExponent = FastInteger.FromBig(desiredExponent);
          if (ctx != null && ctx.getHasFlags() && fastDesiredExponent.compareTo(naturalExponent) > 0) {
            // Treat as rounded if the desired exponent is greater
            // than the "ideal" exponent
            ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagRounded));
          }
          if (expdiff.compareTo(fastDesiredExponent) <= 0) {
            shift = FastInteger.Copy(fastDesiredExponent).Subtract(expdiff);
            BigInteger quo;
{
BigInteger[] divrem=(mantissaDividend).divideAndRemainder(mantissaDivisor);
quo=divrem[0];
rem=divrem[1]; }
            return this.RoundToScale(quo, rem, mantissaDivisor, desiredExponent, shift, resultNeg, ctx);
          } else if (ctx != null && ctx.getPrecision().signum() != 0 && FastInteger.Copy(expdiff).SubtractInt(8).compareTo(fastPrecision) > 0) {
            // NOTE: 8 guard digits
            // Result would require a too-high precision since
            // exponent difference is much higher
            return this.SignalInvalidWithMessage(ctx, "Result can't fit the precision");
          } else {
            shift = FastInteger.Copy(expdiff).Subtract(fastDesiredExponent);
            mantissaDividend = this.helper.MultiplyByRadixPower(mantissaDividend, shift);
            BigInteger quo;
{
BigInteger[] divrem=(mantissaDividend).divideAndRemainder(mantissaDivisor);
quo=divrem[0];
rem=divrem[1]; }
            return this.RoundToScale(
              quo,
              rem,
              mantissaDivisor,
              desiredExponent,
              new FastInteger(0),
              resultNeg,
              ctx);
          }
        }
        if (integerMode == IntegerModeRegular) {
          BigInteger rem = null;
          BigInteger quo = null;
          // System.out.println("div={0} divs={1}",mantissaDividend.getUnsignedBitLength(),
          // mantissaDivisor.getUnsignedBitLength());
          if ((mantissaDividend.remainder(mantissaDivisor)).signum()==0) {
            // Dividend is divisible by divisor
            quo = mantissaDividend.divide(mantissaDivisor);
            if (resultNeg) {
              quo=quo.negate();
            }
            return this.RoundToPrecision(this.helper.CreateNewWithFlags(quo, naturalExponent.AsBigInteger(), resultNeg ? BigNumberFlags.FlagNegative : 0), ctx);
          }
          if (hasPrecision) {

            BigInteger divid = mantissaDividend;
            FastInteger shift = FastInteger.FromBig(ctx.getPrecision());
            dividendPrecision = this.helper.CreateShiftAccumulator(mantissaDividend).GetDigitLength();
            divisorPrecision = this.helper.CreateShiftAccumulator(mantissaDivisor).GetDigitLength();
            if (dividendPrecision.compareTo(divisorPrecision) <= 0) {
              divisorPrecision.Subtract(dividendPrecision);
              divisorPrecision.Increment();
              shift.Add(divisorPrecision);
              divid = this.helper.MultiplyByRadixPower(divid, shift);
            } else {
              // Already greater than divisor precision
              dividendPrecision.Subtract(divisorPrecision);
              if (dividendPrecision.compareTo(shift) <= 0) {
                shift.Subtract(dividendPrecision);
                shift.Increment();
                divid = this.helper.MultiplyByRadixPower(divid, shift);
              } else {
                // no need to shift
                shift.SetInt(0);
              }
            }
            dividendPrecision = this.helper.CreateShiftAccumulator(divid).GetDigitLength();
            divisorPrecision = this.helper.CreateShiftAccumulator(mantissaDivisor).GetDigitLength();
            if (shift.signum() != 0 || quo == null) {
              // if shift isn't zero, recalculate the quotient
              // and remainder
              {
BigInteger[] divrem=(divid).divideAndRemainder(mantissaDivisor);
quo=divrem[0];
rem=divrem[1]; }
            }
            int[] digitStatus = this.RoundToScaleStatus(rem, mantissaDivisor, ctx);
            if (digitStatus == null) {
              return this.SignalInvalidWithMessage(ctx, "Rounding was required");
            }
            FastInteger natexp = FastInteger.Copy(naturalExponent).Subtract(shift);
            PrecisionContext ctxcopy = ctx.WithBlankFlags();
            T retval2 = this.helper.CreateNewWithFlags(quo, natexp.AsBigInteger(), resultNeg ? BigNumberFlags.FlagNegative : 0);
            retval2 = this.RoundToPrecisionWithShift(retval2, ctxcopy, digitStatus[0], digitStatus[1], null, false);
            if ((ctxcopy.getFlags() & PrecisionContext.FlagInexact) != 0) {
              if (ctx.getHasFlags()) {
                ctx.setFlags(ctx.getFlags()|(ctxcopy.getFlags()));
              }
              return retval2;
            } else {
              if (ctx.getHasFlags()) {
                ctx.setFlags(ctx.getFlags()|(ctxcopy.getFlags()));
                ctx.setFlags(ctx.getFlags()&~(PrecisionContext.FlagRounded));
              }
              return this.ReduceToPrecisionAndIdealExponent(retval2, ctx, rem.signum()==0 ? null : fastPrecision, expdiff);
            }
          }
        }
        // Rest of method assumes unlimited precision
        // and IntegerModeRegular
        int mantcmp = mantissaDividend.compareTo(mantissaDivisor);
        if (mantcmp < 0) {
          // dividend mantissa is less than divisor mantissa
          dividendPrecision = this.helper.CreateShiftAccumulator(mantissaDividend).GetDigitLength();
          divisorPrecision = this.helper.CreateShiftAccumulator(mantissaDivisor).GetDigitLength();
          divisorPrecision.Subtract(dividendPrecision);
          if (divisorPrecision.isValueZero()) {
            divisorPrecision.Increment();
          }
          // multiply dividend mantissa so precisions are the same
          // (except if they're already the same, in which case multiply
          // by radix)
          mantissaDividend = this.helper.MultiplyByRadixPower(mantissaDividend, divisorPrecision);
          adjust.Add(divisorPrecision);
          if (mantissaDividend.compareTo(mantissaDivisor) < 0) {
            // dividend mantissa is still less, multiply once more
            if (radix == 2) {
              mantissaDividend=mantissaDividend.shiftLeft(1);
            } else {
              mantissaDividend=mantissaDividend.multiply(BigInteger.valueOf(radix));
            }
            adjust.Increment();
          }
        } else if (mantcmp > 0) {
          // dividend mantissa is greater than divisor mantissa
          dividendPrecision = this.helper.CreateShiftAccumulator(mantissaDividend).GetDigitLength();
          divisorPrecision = this.helper.CreateShiftAccumulator(mantissaDivisor).GetDigitLength();
          dividendPrecision.Subtract(divisorPrecision);
          BigInteger oldMantissaB = mantissaDivisor;
          mantissaDivisor = this.helper.MultiplyByRadixPower(mantissaDivisor, dividendPrecision);
          adjust.Subtract(dividendPrecision);
          if (mantissaDividend.compareTo(mantissaDivisor) < 0) {
            // dividend mantissa is now less, divide by radix power
            if (dividendPrecision.CompareToInt(1) == 0) {
              // no need to divide here, since that would just undo
              // the multiplication
              mantissaDivisor = oldMantissaB;
            } else {
              BigInteger bigpow = BigInteger.valueOf(radix);
              mantissaDivisor=mantissaDivisor.divide(bigpow);
            }
            adjust.Increment();
          }
        }
        if (mantcmp == 0) {
          result = new FastInteger(1);
          mantissaDividend = BigInteger.ZERO;
        } else {
          {
            if (!this.helper.HasTerminatingRadixExpansion(mantissaDividend, mantissaDivisor)) {
              return this.SignalInvalidWithMessage(ctx, "Result would have a nonterminating expansion");
            }
            FastInteger divs = FastInteger.FromBig(mantissaDivisor);
            FastInteger divd = FastInteger.FromBig(mantissaDividend);
            boolean divisorFits = divs.CanFitInInt32();
            int smallDivisor = divisorFits ? divs.AsInt32() : 0;
            int halfRadix = radix / 2;
            FastInteger divsHalfRadix = null;
            if (radix != 2) {
              divsHalfRadix = FastInteger.FromBig(mantissaDivisor).Multiply(halfRadix);
            }
            while (true) {
              boolean remainderZero = false;
              int count = 0;
              if (divd.CanFitInInt32()) {
                if (divisorFits) {
                  int smallDividend = divd.AsInt32();
                  count = smallDividend / smallDivisor;
                  divd.SetInt(smallDividend % smallDivisor);
                } else {
                  count = 0;
                }
              } else {
                if (divsHalfRadix != null) {
                  count += halfRadix * divd.RepeatedSubtract(divsHalfRadix);
                }
                count += divd.RepeatedSubtract(divs);
              }
              result.AddInt(count);
              remainderZero = divd.isValueZero();
              if (remainderZero && adjust.signum() >= 0) {
                mantissaDividend = divd.AsBigInteger();
                break;
              }
              adjust.Increment();
              result.Multiply(radix);
              divd.Multiply(radix);
            }
          }
        }
        // mantissaDividend now has the remainder
        FastInteger exp = FastInteger.Copy(expdiff).Subtract(adjust);
        Rounding rounding = (ctx == null) ? Rounding.HalfEven : ctx.getRounding();
        int lastDiscarded = 0;
        int olderDiscarded = 0;
        if (mantissaDividend.signum()!=0) {
          if (rounding == Rounding.HalfDown || rounding == Rounding.HalfEven || rounding == Rounding.HalfUp) {
            BigInteger halfDivisor = mantissaDivisor.shiftRight(1);
            int cmpHalf = mantissaDividend.compareTo(halfDivisor);
            if ((cmpHalf == 0) && mantissaDivisor.testBit(0)==false) {
              // remainder is exactly half
              lastDiscarded = radix / 2;
              olderDiscarded = 0;
            } else if (cmpHalf > 0) {
              // remainder is greater than half
              lastDiscarded = radix / 2;
              olderDiscarded = 1;
            } else {
              // remainder is less than half
              lastDiscarded = 0;
              olderDiscarded = 1;
            }
          } else {
            if (rounding == Rounding.Unnecessary) {
              return this.SignalInvalidWithMessage(ctx, "Rounding was required");
            }
            lastDiscarded = 1;
            olderDiscarded = 1;
          }
        }
        BigInteger bigResult = result.AsBigInteger();
        if (ctx != null && ctx.getHasFlags() && exp.compareTo(expdiff) > 0) {
          // Treat as rounded if the true exponent is greater
          // than the "ideal" exponent
          ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagRounded));
        }
        BigInteger bigexp = exp.AsBigInteger();
        T retval = this.helper.CreateNewWithFlags(bigResult, bigexp, resultNeg ? BigNumberFlags.FlagNegative : 0);
        return this.RoundToPrecisionWithShift(retval, ctx, lastDiscarded, olderDiscarded, null, false);
      }
    }

    /**
     * Gets the lesser value between two values, ignoring their signs. If
     * the absolute values are equal, has the same effect as Min.
     * @param a A T object. (2).
     * @param b A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T MinMagnitude(T a, T b, PrecisionContext ctx) {
      if (a == null) {
        throw new NullPointerException("a");
      }
      if (b == null) {
        throw new NullPointerException("b");
      }
      // Handle infinity and NaN
      T result = this.MinMaxHandleSpecial(a, b, ctx, true, true);
      if ((Object)result != (Object)null) {
        return result;
      }
      int cmp = this.compareTo(this.AbsRaw(a), this.AbsRaw(b));
      if (cmp == 0) {
        return this.Min(a, b, ctx);
      }
      return (cmp < 0) ? this.RoundToPrecision(a, ctx) : this.RoundToPrecision(b, ctx);
    }

    /**
     * Gets the greater value between two values, ignoring their signs.
     * If the absolute values are equal, has the same effect as Max.
     * @param a A T object. (2).
     * @param b A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T MaxMagnitude(T a, T b, PrecisionContext ctx) {
      if (a == null) {
        throw new NullPointerException("a");
      }
      if (b == null) {
        throw new NullPointerException("b");
      }
      // Handle infinity and NaN
      T result = this.MinMaxHandleSpecial(a, b, ctx, false, true);
      if ((Object)result != (Object)null) {
        return result;
      }
      int cmp = this.compareTo(this.AbsRaw(a), this.AbsRaw(b));
      if (cmp == 0) {
        return this.Max(a, b, ctx);
      }
      return (cmp > 0) ? this.RoundToPrecision(a, ctx) : this.RoundToPrecision(b, ctx);
    }

    /**
     * Gets the greater value between two T values.
     * @param a A T object.
     * @param b A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return The larger value of the two objects.
     */
    public T Max(T a, T b, PrecisionContext ctx) {
      if (a == null) {
        throw new NullPointerException("a");
      }
      if (b == null) {
        throw new NullPointerException("b");
      }
      // Handle infinity and NaN
      T result = this.MinMaxHandleSpecial(a, b, ctx, false, false);
      if ((Object)result != (Object)null) {
        return result;
      }
      int cmp = this.compareTo(a, b);
      if (cmp != 0) {
        return cmp < 0 ? this.RoundToPrecision(b, ctx) : this.RoundToPrecision(a, ctx);
      }
      int flagNegA = this.helper.GetFlags(a) & BigNumberFlags.FlagNegative;
      if (flagNegA != (this.helper.GetFlags(b) & BigNumberFlags.FlagNegative)) {
        return (flagNegA != 0) ? this.RoundToPrecision(b, ctx) : this.RoundToPrecision(a, ctx);
      }
      if (flagNegA == 0) {
        return this.helper.GetExponent(a).compareTo(this.helper.GetExponent(b)) > 0 ? this.RoundToPrecision(a, ctx) : this.RoundToPrecision(b, ctx);
      } else {
        return this.helper.GetExponent(a).compareTo(this.helper.GetExponent(b)) > 0 ? this.RoundToPrecision(b, ctx) : this.RoundToPrecision(a, ctx);
      }
    }

    /**
     * Gets the lesser value between two T values.
     * @param a A T object.
     * @param b A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return The smaller value of the two objects.
     */
    public T Min(T a, T b, PrecisionContext ctx) {
      if (a == null) {
        throw new NullPointerException("a");
      }
      if (b == null) {
        throw new NullPointerException("b");
      }
      // Handle infinity and NaN
      T result = this.MinMaxHandleSpecial(a, b, ctx, true, false);
      if ((Object)result != (Object)null) {
        return result;
      }
      int cmp = this.compareTo(a, b);
      if (cmp != 0) {
        return cmp > 0 ? this.RoundToPrecision(b, ctx) : this.RoundToPrecision(a, ctx);
      }
      int signANeg = this.helper.GetFlags(a) & BigNumberFlags.FlagNegative;
      if (signANeg != (this.helper.GetFlags(b) & BigNumberFlags.FlagNegative)) {
        return (signANeg != 0) ? this.RoundToPrecision(a, ctx) : this.RoundToPrecision(b, ctx);
      }
      if (signANeg == 0) {
        return this.helper.GetExponent(a).compareTo(this.helper.GetExponent(b)) > 0 ? this.RoundToPrecision(b, ctx) : this.RoundToPrecision(a, ctx);
      } else {
        return this.helper.GetExponent(a).compareTo(this.helper.GetExponent(b)) > 0 ? this.RoundToPrecision(a, ctx) : this.RoundToPrecision(b, ctx);
      }
    }

    /**
     * Multiplies two T objects.
     * @param thisValue A T object.
     * @param other A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return The product of the two objects.
     */
    public T Multiply(T thisValue, T other, PrecisionContext ctx) {
      int thisFlags = this.helper.GetFlags(thisValue);
      int otherFlags = this.helper.GetFlags(other);
      if (((thisFlags | otherFlags) & BigNumberFlags.FlagSpecial) != 0) {
        T result = this.HandleNotANumber(thisValue, other, ctx);
        if ((Object)result != (Object)null) {
          return result;
        }
        if ((thisFlags & BigNumberFlags.FlagInfinity) != 0) {
          // Attempt to multiply infinity by 0
          if ((otherFlags & BigNumberFlags.FlagSpecial) == 0 && this.helper.GetMantissa(other).signum()==0) {
            return this.SignalInvalid(ctx);
          }
          return this.EnsureSign(thisValue, ((thisFlags & BigNumberFlags.FlagNegative) ^ (otherFlags & BigNumberFlags.FlagNegative)) != 0);
        }
        if ((otherFlags & BigNumberFlags.FlagInfinity) != 0) {
          // Attempt to multiply infinity by 0
          if ((thisFlags & BigNumberFlags.FlagSpecial) == 0 && this.helper.GetMantissa(thisValue).signum()==0) {
            return this.SignalInvalid(ctx);
          }
          return this.EnsureSign(other, ((thisFlags & BigNumberFlags.FlagNegative) ^ (otherFlags & BigNumberFlags.FlagNegative)) != 0);
        }
      }
      BigInteger bigintOp2 = this.helper.GetExponent(other);
      BigInteger newexp = this.helper.GetExponent(thisValue).add(bigintOp2);
      BigInteger mantissaOp2 = this.helper.GetMantissa(other);
      thisFlags = (thisFlags & BigNumberFlags.FlagNegative) ^ (otherFlags & BigNumberFlags.FlagNegative);
      T ret = this.helper.CreateNewWithFlags(this.helper.GetMantissa(thisValue).multiply(mantissaOp2), newexp, thisFlags);
      if (ctx != null) {
        ret = this.RoundToPrecision(ret, ctx);
      }
      return ret;
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
      PrecisionContext ctx2 = PrecisionContext.Unlimited.WithBlankFlags();
      T ret = this.MultiplyAddHandleSpecial(thisValue, multiplicand, augend, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      ret = this.Add(this.Multiply(thisValue, multiplicand, ctx2), augend, ctx);
      if (ctx != null && ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(ctx2.getFlags()));
      }
      return ret;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param context A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToBinaryPrecision(T thisValue, PrecisionContext context) {
      return this.RoundToBinaryPrecisionWithShift(thisValue, context, 0, 0, null, false);
    }

    private T RoundToBinaryPrecisionWithShift(T thisValue, PrecisionContext context, int lastDiscarded, int olderDiscarded, FastInteger shift, boolean adjustNegativeZero) {
      return this.RoundToPrecisionInternal(thisValue, lastDiscarded, olderDiscarded, shift, true, adjustNegativeZero, context);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param context A PrecisionContext object.
     * @return A T object.
     */
    public T Plus(T thisValue, PrecisionContext context) {
      return this.RoundToPrecisionInternal(thisValue, 0, 0, null, false, true, context);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param context A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToPrecision(T thisValue, PrecisionContext context) {
      return this.RoundToPrecisionInternal(thisValue, 0, 0, null, false, false, context);
    }

    private T RoundToPrecisionWithShift(T thisValue, PrecisionContext context, int lastDiscarded, int olderDiscarded, FastInteger shift, boolean adjustNegativeZero) {
      return this.RoundToPrecisionInternal(thisValue, lastDiscarded, olderDiscarded, shift, false, adjustNegativeZero, context);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param otherValue A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Quantize(T thisValue, T otherValue, PrecisionContext ctx) {
      int thisFlags = this.helper.GetFlags(thisValue);
      int otherFlags = this.helper.GetFlags(otherValue);
      if (((thisFlags | otherFlags) & BigNumberFlags.FlagSpecial) != 0) {
        T result = this.HandleNotANumber(thisValue, otherValue, ctx);
        if ((Object)result != (Object)null) {
          return result;
        }
        if (((thisFlags & otherFlags) & BigNumberFlags.FlagInfinity) != 0) {
          return this.RoundToPrecision(thisValue, ctx);
        }
        if (((thisFlags | otherFlags) & BigNumberFlags.FlagInfinity) != 0) {
          return this.SignalInvalid(ctx);
        }
      }
      BigInteger expOther = this.helper.GetExponent(otherValue);
      if (ctx != null && !ctx.ExponentWithinRange(expOther)) {
        return this.SignalInvalidWithMessage(ctx, "Exponent not within exponent range: " + expOther.toString());
      }
      PrecisionContext tmpctx = (ctx == null ? PrecisionContext.ForRounding(Rounding.HalfEven) : ctx.Copy()).WithBlankFlags();
      BigInteger mantThis = (this.helper.GetMantissa(thisValue)).abs();
      BigInteger expThis = this.helper.GetExponent(thisValue);
      int expcmp = expThis.compareTo(expOther);
      int negativeFlag = this.helper.GetFlags(thisValue) & BigNumberFlags.FlagNegative;
      T ret = null;
      if (expcmp == 0) {
        ret = this.RoundToPrecision(thisValue, tmpctx);
      } else if (mantThis.signum()==0) {
        ret = this.helper.CreateNewWithFlags(BigInteger.ZERO, expOther, negativeFlag);
        ret = this.RoundToPrecision(ret, tmpctx);
      } else if (expcmp > 0) {
        // Other exponent is less
        FastInteger radixPower = FastInteger.FromBig(expThis).SubtractBig(expOther);
        if (tmpctx.getPrecision().signum() > 0 && radixPower.compareTo(FastInteger.FromBig(tmpctx.getPrecision()).AddInt(10)) > 0) {
          // Radix power is much too high for the current precision
          return this.SignalInvalidWithMessage(ctx, "Result too high for current precision");
        }
        mantThis = this.helper.MultiplyByRadixPower(mantThis, radixPower);
        ret = this.helper.CreateNewWithFlags(mantThis, expOther, negativeFlag);
        ret = this.RoundToPrecision(ret, tmpctx);
      } else {
        // Other exponent is greater
        FastInteger shift = FastInteger.FromBig(expOther).SubtractBig(expThis);
        ret = this.RoundToPrecisionWithShift(thisValue, tmpctx, 0, 0, shift, false);
      }
      if ((tmpctx.getFlags() & PrecisionContext.FlagOverflow) != 0) {
        return this.SignalInvalid(ctx);
      }
      if (ret == null || !this.helper.GetExponent(ret).equals(expOther)) {
        return this.SignalInvalid(ctx);
      }
      ret = this.EnsureSign(ret, negativeFlag != 0);
      if (ctx != null && ctx.getHasFlags()) {
        int flags = tmpctx.getFlags();
        flags &= ~PrecisionContext.FlagUnderflow;
        ctx.setFlags(ctx.getFlags()|(flags));
      }
      return ret;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param expOther A BigInteger object.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToExponentExact(T thisValue, BigInteger expOther, PrecisionContext ctx) {
      if (this.helper.GetExponent(thisValue).compareTo(expOther) >= 0) {
        return this.RoundToPrecision(thisValue, ctx);
      } else {
        PrecisionContext pctx = (ctx == null) ? null : ctx.WithPrecision(0).WithBlankFlags();
        T ret = this.Quantize(thisValue, this.helper.CreateNewWithFlags(BigInteger.ONE, expOther, 0), pctx);
        if (ctx != null && ctx.getHasFlags()) {
          ctx.setFlags(ctx.getFlags()|(pctx.getFlags()));
        }
        return ret;
      }
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param expOther A BigInteger object.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToExponentSimple(T thisValue, BigInteger expOther, PrecisionContext ctx) {
      int thisFlags = this.helper.GetFlags(thisValue);
      if ((thisFlags & BigNumberFlags.FlagSpecial) != 0) {
        T result = this.HandleNotANumber(thisValue, thisValue, ctx);
        if ((Object)result != (Object)null) {
          return result;
        }
        if ((thisFlags & BigNumberFlags.FlagInfinity) != 0) {
          return thisValue;
        }
      }
      if (this.helper.GetExponent(thisValue).compareTo(expOther) >= 0) {
        return this.RoundToPrecision(thisValue, ctx);
      } else {
        if (ctx != null && !ctx.ExponentWithinRange(expOther)) {
          return this.SignalInvalidWithMessage(ctx, "Exponent not within exponent range: " + expOther.toString());
        }
        BigInteger bigmantissa = (this.helper.GetMantissa(thisValue)).abs();
        FastInteger shift = FastInteger.FromBig(expOther).SubtractBig(this.helper.GetExponent(thisValue));
        IShiftAccumulator accum = this.helper.CreateShiftAccumulator(bigmantissa);
        accum.ShiftRight(shift);
        bigmantissa = accum.getShiftedInt();
        thisValue = this.helper.CreateNewWithFlags(bigmantissa, expOther, thisFlags);
        return this.RoundToPrecisionWithShift(thisValue, ctx, accum.getLastDiscardedDigit(), accum.getOlderDiscardedDigits(), null, false);
      }
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param exponent A BigInteger object.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToExponentNoRoundedFlag(T thisValue, BigInteger exponent, PrecisionContext ctx) {
      PrecisionContext pctx = (ctx == null) ? null : ctx.WithBlankFlags();
      T ret = this.RoundToExponentExact(thisValue, exponent, pctx);
      if (ctx != null && ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(pctx.getFlags() & ~(PrecisionContext.FlagInexact | PrecisionContext.FlagRounded)));
      }
      return ret;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @param precision A FastInteger object.
     * @param idealExp A FastInteger object. (2).
     * @return A T object.
     */
    private T ReduceToPrecisionAndIdealExponent(T thisValue, PrecisionContext ctx, FastInteger precision, FastInteger idealExp) {
      T ret = this.RoundToPrecision(thisValue, ctx);
      if (ret != null && (this.helper.GetFlags(ret) & BigNumberFlags.FlagSpecial) == 0) {
        BigInteger bigmant = (this.helper.GetMantissa(ret)).abs();
        FastInteger exp = FastInteger.FromBig(this.helper.GetExponent(ret));
        if (bigmant.signum()==0) {
          exp = new FastInteger(0);
        } else {
          int radix = this.thisRadix;
          FastInteger digits = (precision == null) ? null : this.helper.CreateShiftAccumulator(bigmant).GetDigitLength();
          BigInteger bigradix = BigInteger.valueOf(radix);
          while (bigmant.signum()!=0) {
            if (precision != null && digits.compareTo(precision) == 0) {
              break;
            }
            if (idealExp != null && exp.compareTo(idealExp) == 0) {
              break;
            }
            BigInteger bigrem;
            BigInteger bigquo;
{
BigInteger[] divrem=(bigmant).divideAndRemainder(bigradix);
bigquo=divrem[0];
bigrem=divrem[1]; }
            if (bigrem.signum()!=0) {
              break;
            }
            bigmant = bigquo;
            exp.Increment();
            if (digits != null) {
              digits.Decrement();
            }
          }
        }
        int flags = this.helper.GetFlags(thisValue);
        ret = this.helper.CreateNewWithFlags(bigmant, exp.AsBigInteger(), flags);
        if (ctx != null && ctx.getClampNormalExponents()) {
          PrecisionContext ctxtmp = ctx.WithBlankFlags();
          ret = this.RoundToPrecision(ret, ctxtmp);
          if (ctx.getHasFlags()) {
            ctx.setFlags(ctx.getFlags()|(ctxtmp.getFlags() & ~PrecisionContext.FlagClamped));
          }
        }
        ret = this.EnsureSign(ret, (flags & BigNumberFlags.FlagNegative) != 0);
      }
      return ret;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Reduce(T thisValue, PrecisionContext ctx) {
      return this.ReduceToPrecisionAndIdealExponent(thisValue, ctx, null, null);
    }

    // whether "precision" is the number of bits and not digits
    private T RoundToPrecisionInternal(T thisValue, int lastDiscarded, int olderDiscarded, FastInteger shift, boolean binaryPrec, boolean adjustNegativeZero, PrecisionContext ctx) {
      if (ctx == null) {
        ctx = PrecisionContext.Unlimited.WithRounding(Rounding.HalfEven);
      }
      // If context has unlimited precision and exponent range,
      // and no discarded digits or shifting
      if (ctx.getPrecision().signum()==0 && !ctx.getHasExponentRange() && (lastDiscarded | olderDiscarded) == 0 && (shift == null || shift.isValueZero())) {
        return thisValue;
      }
      int thisFlags = this.helper.GetFlags(thisValue);
      if ((thisFlags & BigNumberFlags.FlagSpecial) != 0) {
        if ((thisFlags & BigNumberFlags.FlagSignalingNaN) != 0) {
          if (ctx.getHasFlags()) {
            ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInvalid));
          }
          return this.ReturnQuietNaN(thisValue, ctx);
        }
        if ((thisFlags & BigNumberFlags.FlagQuietNaN) != 0) {
          return this.ReturnQuietNaN(thisValue, ctx);
        }
        if ((thisFlags & BigNumberFlags.FlagInfinity) != 0) {
          return thisValue;
        }
      }
      // get the precision
      FastInteger fastPrecision = ctx.getPrecision().canFitInInt() ? new FastInteger(ctx.getPrecision().intValue()) : FastInteger.FromBig(ctx.getPrecision());
      if (fastPrecision.signum() < 0) {
        return this.SignalInvalidWithMessage(ctx, "precision" + " not greater or equal to " + "0" + " (" + fastPrecision + ")");
      }
      if (this.thisRadix == 2 || fastPrecision.isValueZero()) {
        // "binaryPrec" will have no special effect here
        binaryPrec = false;
      }
      IShiftAccumulator accum = null;
      FastInteger fastEMin = null;
      FastInteger fastEMax = null;
      // get the exponent range
      if (ctx != null && ctx.getHasExponentRange()) {
        fastEMax = ctx.getEMax().canFitInInt() ? new FastInteger(ctx.getEMax().intValue()) : FastInteger.FromBig(ctx.getEMax());
        fastEMin = ctx.getEMin().canFitInInt() ? new FastInteger(ctx.getEMin().intValue()) : FastInteger.FromBig(ctx.getEMin());
      }
      Rounding rounding = (ctx == null) ? Rounding.HalfEven : ctx.getRounding();
      boolean unlimitedPrec = fastPrecision.isValueZero();
      if (!binaryPrec) {
        // Fast path to check if rounding is necessary at all
        if (fastPrecision.signum() > 0 && (shift == null || shift.isValueZero()) && (thisFlags & BigNumberFlags.FlagSpecial) == 0) {
          BigInteger mantabs = (this.helper.GetMantissa(thisValue)).abs();
          if (adjustNegativeZero && (thisFlags & BigNumberFlags.FlagNegative) != 0 && mantabs.signum()==0 && (ctx.getRounding() != Rounding.Floor)) {
            // Change negative zero to positive zero
            // except if the rounding mode is Floor
            thisValue = this.EnsureSign(thisValue, false);
            thisFlags = 0;
          }
          accum = this.helper.CreateShiftAccumulatorWithDigits(mantabs, lastDiscarded, olderDiscarded);
          FastInteger digitCount = accum.GetDigitLength();
          if (digitCount.compareTo(fastPrecision) <= 0) {
            if (!this.RoundGivenDigits(lastDiscarded, olderDiscarded, ctx.getRounding(), (thisFlags & BigNumberFlags.FlagNegative) != 0, mantabs)) {
              if (ctx.getHasFlags() && (lastDiscarded | olderDiscarded) != 0) {
                ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInexact | PrecisionContext.FlagRounded));
              }
              if (!ctx.getHasExponentRange()) {
                return thisValue;
              }
              BigInteger bigexp = this.helper.GetExponent(thisValue);
              FastInteger fastExp = bigexp.canFitInInt() ? new FastInteger(bigexp.intValue()) : FastInteger.FromBig(bigexp);
              FastInteger fastAdjustedExp = FastInteger.Copy(fastExp).Add(fastPrecision).Decrement();
              FastInteger fastNormalMin = FastInteger.Copy(fastEMin).Add(fastPrecision).Decrement();
              if (fastAdjustedExp.compareTo(fastEMax) <= 0 && fastAdjustedExp.compareTo(fastNormalMin) >= 0) {
                return thisValue;
              }
            } else {
              if (ctx.getHasFlags() && (lastDiscarded | olderDiscarded) != 0) {
                ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInexact | PrecisionContext.FlagRounded));
              }
              boolean stillWithinPrecision = false;
              mantabs=mantabs.add(BigInteger.ONE);
              if (digitCount.compareTo(fastPrecision) < 0) {
                stillWithinPrecision = true;
              } else {
                BigInteger radixPower = this.helper.MultiplyByRadixPower(BigInteger.ONE, fastPrecision);
                stillWithinPrecision = mantabs.compareTo(radixPower) < 0;
              }
              if (stillWithinPrecision) {
                if (!ctx.getHasExponentRange()) {
                  return this.helper.CreateNewWithFlags(mantabs, this.helper.GetExponent(thisValue), thisFlags);
                }
                BigInteger bigexp = this.helper.GetExponent(thisValue);
                FastInteger fastExp = bigexp.canFitInInt() ? new FastInteger(bigexp.intValue()) : FastInteger.FromBig(bigexp);
                FastInteger fastAdjustedExp = FastInteger.Copy(fastExp).Add(fastPrecision).Decrement();
                FastInteger fastNormalMin = FastInteger.Copy(fastEMin).Add(fastPrecision).Decrement();
                if (fastAdjustedExp.compareTo(fastEMax) <= 0 && fastAdjustedExp.compareTo(fastNormalMin) >= 0) {
                  return this.helper.CreateNewWithFlags(mantabs, bigexp, thisFlags);
                }
              }
            }
          }
        }
      }
      if (adjustNegativeZero && (thisFlags & BigNumberFlags.FlagNegative) != 0 && this.helper.GetMantissa(thisValue).signum()==0 && (rounding != Rounding.Floor)) {
        // Change negative zero to positive zero
        // except if the rounding mode is Floor
        thisValue = this.EnsureSign(thisValue, false);
        thisFlags = 0;
      }
      boolean neg = (thisFlags & BigNumberFlags.FlagNegative) != 0;
      BigInteger bigmantissa = (this.helper.GetMantissa(thisValue)).abs();
      // save mantissa in case result is subnormal
      // and must be rounded again
      BigInteger oldmantissa = bigmantissa;
      boolean mantissaWasZero = oldmantissa.signum()==0 && (lastDiscarded | olderDiscarded) == 0;
      BigInteger maxMantissa = BigInteger.ONE;
      FastInteger exp = FastInteger.FromBig(this.helper.GetExponent(thisValue));
      int flags = 0;
      if (accum == null) {
        accum = this.helper.CreateShiftAccumulatorWithDigits(bigmantissa, lastDiscarded, olderDiscarded);
      }
      if (binaryPrec) {
        FastInteger prec = FastInteger.Copy(fastPrecision);
        while (prec.signum() > 0) {
          int bitShift = (prec.CompareToInt(1000000) >= 0) ? 1000000 : prec.AsInt32();
          maxMantissa=maxMantissa.shiftLeft(bitShift);
          prec.SubtractInt(bitShift);
        }
        maxMantissa=maxMantissa.subtract(BigInteger.ONE);
        IShiftAccumulator accumMaxMant = this.helper.CreateShiftAccumulator(maxMantissa);
        // Get the digit length of the maximum possible mantissa
        // for the given binary precision, and use that for
        // fastPrecision
        fastPrecision = accumMaxMant.GetDigitLength();
      }
      if (shift != null && shift.signum() != 0) {
        accum.ShiftRight(shift);
      }
      if (!unlimitedPrec) {
        accum.ShiftToDigits(fastPrecision);
      } else {
        fastPrecision = accum.GetDigitLength();
      }
      if (binaryPrec) {
        while (accum.getShiftedInt().compareTo(maxMantissa) > 0) {
          accum.ShiftRightInt(1);
        }
      }
      FastInteger discardedBits = FastInteger.Copy(accum.getDiscardedDigitCount());
      exp.Add(discardedBits);
      FastInteger adjExponent = FastInteger.Copy(exp).Add(accum.GetDigitLength()).Decrement();
      // System.out.println("{0}->{1} digits={2} exp={3} [curexp={4}] adj={5},max={6}",bigmantissa,accum.getShiftedInt(),
      // accum.getDiscardedDigitCount(), exp, helper.GetExponent(thisValue), adjExponent, fastEMax);
      FastInteger newAdjExponent = adjExponent;
      FastInteger clamp = null;
      BigInteger earlyRounded = BigInteger.ZERO;
      if (binaryPrec && fastEMax != null && adjExponent.compareTo(fastEMax) == 0) {
        // May or may not be an overflow depending on the mantissa
        FastInteger expdiff = FastInteger.Copy(fastPrecision).Subtract(accum.GetDigitLength());
        BigInteger currMantissa = accum.getShiftedInt();
        currMantissa = this.helper.MultiplyByRadixPower(currMantissa, expdiff);
        if (currMantissa.compareTo(maxMantissa) > 0) {
          // Mantissa too high, treat as overflow
          adjExponent.Increment();
        }
      }
      // System.out.println("{0} adj={1} emin={2}",thisValue,adjExponent,fastEMin);
      if (ctx.getHasFlags() && fastEMin != null && !unlimitedPrec && adjExponent.compareTo(fastEMin) < 0) {
        earlyRounded = accum.getShiftedInt();
        if (this.RoundGivenBigInt(accum, rounding, neg, earlyRounded)) {
          earlyRounded=earlyRounded.add(BigInteger.ONE);
          if (earlyRounded.testBit(0)==false || (this.thisRadix & 1) != 0) {
            IShiftAccumulator accum2 = this.helper.CreateShiftAccumulator(earlyRounded);
            FastInteger newDigitLength = accum2.GetDigitLength();
            // Ensure newDigitLength doesn't exceed precision
            if (binaryPrec || newDigitLength.compareTo(fastPrecision) > 0) {
              newDigitLength = FastInteger.Copy(fastPrecision);
            }
            newAdjExponent = FastInteger.Copy(exp).Add(newDigitLength).Decrement();
          }
        }
      }
      if (fastEMax != null && adjExponent.compareTo(fastEMax) > 0) {
        if (mantissaWasZero) {
          if (ctx.getHasFlags()) {
            ctx.setFlags(ctx.getFlags()|(flags | PrecisionContext.FlagClamped));
          }
          if (ctx.getClampNormalExponents()) {
            // Clamp exponents to eMax + 1 - precision
            // if directed
            if (binaryPrec && this.thisRadix != 2) {
              fastPrecision = this.helper.CreateShiftAccumulator(maxMantissa).GetDigitLength();
            }
            FastInteger clampExp = FastInteger.Copy(fastEMax).Increment().Subtract(fastPrecision);
            if (fastEMax.compareTo(clampExp) > 0) {
              if (ctx.getHasFlags()) {
                ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagClamped));
              }
              fastEMax = clampExp;
            }
          }
          return this.helper.CreateNewWithFlags(oldmantissa, fastEMax.AsBigInteger(), thisFlags);
        } else {
          // Overflow
          flags |= PrecisionContext.FlagOverflow | PrecisionContext.FlagInexact | PrecisionContext.FlagRounded;
          if (rounding == Rounding.Unnecessary) {
            return this.SignalInvalidWithMessage(ctx, "Rounding was required");
          }
          if (!unlimitedPrec && (rounding == Rounding.Down || rounding == Rounding.ZeroFiveUp || (rounding == Rounding.Ceiling && neg) || (rounding == Rounding.Floor && !neg))) {
            // Set to the highest possible value for
            // the given precision
            BigInteger overflowMant = BigInteger.ZERO;
            if (binaryPrec) {
              overflowMant = maxMantissa;
            } else {
              overflowMant = this.helper.MultiplyByRadixPower(BigInteger.ONE, fastPrecision);
              overflowMant=overflowMant.subtract(BigInteger.ONE);
            }
            if (ctx.getHasFlags()) {
              ctx.setFlags(ctx.getFlags()|(flags));
            }
            clamp = FastInteger.Copy(fastEMax).Increment().Subtract(fastPrecision);
            return this.helper.CreateNewWithFlags(overflowMant, clamp.AsBigInteger(), neg ? BigNumberFlags.FlagNegative : 0);
          }
          if (ctx.getHasFlags()) {
            ctx.setFlags(ctx.getFlags()|(flags));
          }
          return this.SignalOverflow(neg);
        }
      } else if (fastEMin != null && adjExponent.compareTo(fastEMin) < 0) {
        // Subnormal
        FastInteger fastETiny = FastInteger.Copy(fastEMin).Subtract(fastPrecision).Increment();
        if (ctx.getHasFlags()) {
          if (earlyRounded.signum()!=0) {
            if (newAdjExponent.compareTo(fastEMin) < 0) {
              flags |= PrecisionContext.FlagSubnormal;
            }
          }
        }
        // System.out.println("exp={0} eTiny={1}",exp,fastETiny);
        FastInteger subExp = FastInteger.Copy(exp);
        // System.out.println("exp={0} eTiny={1}",subExp,fastETiny);
        if (subExp.compareTo(fastETiny) < 0) {
          // System.out.println("Less than ETiny");
          FastInteger expdiff = FastInteger.Copy(fastETiny).Subtract(exp);
          expdiff.Add(discardedBits);
          accum = this.helper.CreateShiftAccumulatorWithDigits(oldmantissa, lastDiscarded, olderDiscarded);
          accum.ShiftRight(expdiff);
          FastInteger newmantissa = accum.getShiftedIntFast();
          if ((accum.getLastDiscardedDigit() | accum.getOlderDiscardedDigits()) != 0) {
            if (rounding == Rounding.Unnecessary) {
              return this.SignalInvalidWithMessage(ctx, "Rounding was required");
            }
          }
          if (accum.getDiscardedDigitCount().signum() != 0 || (accum.getLastDiscardedDigit() | accum.getOlderDiscardedDigits()) != 0) {
            if (ctx.getHasFlags()) {
              if (!mantissaWasZero) {
                flags |= PrecisionContext.FlagRounded;
              }
              if ((accum.getLastDiscardedDigit() | accum.getOlderDiscardedDigits()) != 0) {
                flags |= PrecisionContext.FlagInexact | PrecisionContext.FlagRounded;
              }
            }
            if (this.Round(accum, rounding, neg, newmantissa)) {
              newmantissa.Increment();
            }
          }
          if (ctx.getHasFlags()) {
            if (newmantissa.isValueZero()) {
              flags |= PrecisionContext.FlagClamped;
            }
            if ((flags & (PrecisionContext.FlagSubnormal | PrecisionContext.FlagInexact)) == (PrecisionContext.FlagSubnormal | PrecisionContext.FlagInexact)) {
              flags |= PrecisionContext.FlagUnderflow | PrecisionContext.FlagRounded;
            }
            ctx.setFlags(ctx.getFlags()|(flags));
          }
          bigmantissa = newmantissa.AsBigInteger();
          if (ctx.getClampNormalExponents()) {
            // Clamp exponents to eMax + 1 - precision
            // if directed
            if (binaryPrec && this.thisRadix != 2) {
              fastPrecision = this.helper.CreateShiftAccumulator(maxMantissa).GetDigitLength();
            }
            FastInteger clampExp = FastInteger.Copy(fastEMax).Increment().Subtract(fastPrecision);
            if (fastETiny.compareTo(clampExp) > 0) {
              if (bigmantissa.signum()!=0) {
                expdiff = FastInteger.Copy(fastETiny).Subtract(clampExp);
                bigmantissa = this.helper.MultiplyByRadixPower(bigmantissa, expdiff);
              }
              if (ctx.getHasFlags()) {
                ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagClamped));
              }
              fastETiny = clampExp;
            }
          }
          return this.helper.CreateNewWithFlags(newmantissa.AsBigInteger(), fastETiny.AsBigInteger(), neg ? BigNumberFlags.FlagNegative : 0);
        }
      }
      boolean recheckOverflow = false;
      if (accum.getDiscardedDigitCount().signum() != 0 || (accum.getLastDiscardedDigit() | accum.getOlderDiscardedDigits()) != 0) {
        if (bigmantissa.signum()!=0) {
          flags |= PrecisionContext.FlagRounded;
        }
        bigmantissa = accum.getShiftedInt();
        if ((accum.getLastDiscardedDigit() | accum.getOlderDiscardedDigits()) != 0) {
          flags |= PrecisionContext.FlagInexact | PrecisionContext.FlagRounded;
          if (rounding == Rounding.Unnecessary) {
            return this.SignalInvalidWithMessage(ctx, "Rounding was required");
          }
        }
        if (this.RoundGivenBigInt(accum, rounding, neg, bigmantissa)) {
          FastInteger oldDigitLength = accum.GetDigitLength();
          bigmantissa=bigmantissa.add(BigInteger.ONE);
          if (binaryPrec) {
            recheckOverflow = true;
          }
          // Check if mantissa's precision is now greater
          // than the one set by the context
          if (!unlimitedPrec && (bigmantissa.testBit(0)==false || (this.thisRadix & 1) != 0) && (binaryPrec || oldDigitLength.compareTo(fastPrecision) >= 0)) {
            accum = this.helper.CreateShiftAccumulator(bigmantissa);
            FastInteger newDigitLength = accum.GetDigitLength();
            if (binaryPrec || newDigitLength.compareTo(fastPrecision) > 0) {
              FastInteger neededShift = FastInteger.Copy(newDigitLength).Subtract(fastPrecision);
              accum.ShiftRight(neededShift);
              if (binaryPrec) {
                while (accum.getShiftedInt().compareTo(maxMantissa) > 0) {
                  accum.ShiftRightInt(1);
                }
              }
              if (accum.getDiscardedDigitCount().signum() != 0) {
                exp.Add(accum.getDiscardedDigitCount());
                discardedBits.Add(accum.getDiscardedDigitCount());
                bigmantissa = accum.getShiftedInt();
                if (!binaryPrec) {
                  recheckOverflow = true;
                }
              }
            }
          }
        }
      }
      if (recheckOverflow && fastEMax != null) {
        // Check for overflow again
        adjExponent = FastInteger.Copy(exp);
        adjExponent.Add(accum.GetDigitLength()).Decrement();
        if (binaryPrec && fastEMax != null && adjExponent.compareTo(fastEMax) == 0) {
          // May or may not be an overflow depending on the mantissa
          // (uses accumulator from previous steps, including the check
          // if the mantissa now exceeded the precision)
          FastInteger expdiff = FastInteger.Copy(fastPrecision).Subtract(accum.GetDigitLength());
          BigInteger currMantissa = accum.getShiftedInt();
          currMantissa = this.helper.MultiplyByRadixPower(currMantissa, expdiff);
          if (currMantissa.compareTo(maxMantissa) > 0) {
            // Mantissa too high, treat as overflow
            adjExponent.Increment();
          }
        }
        if (adjExponent.compareTo(fastEMax) > 0) {
          flags |= PrecisionContext.FlagOverflow | PrecisionContext.FlagInexact | PrecisionContext.FlagRounded;
          if (!unlimitedPrec && (rounding == Rounding.Down || rounding == Rounding.ZeroFiveUp || (rounding == Rounding.Ceiling && neg) || (rounding == Rounding.Floor && !neg))) {
            // Set to the highest possible value for
            // the given precision
            BigInteger overflowMant = BigInteger.ZERO;
            if (binaryPrec) {
              overflowMant = maxMantissa;
            } else {
              overflowMant = this.helper.MultiplyByRadixPower(BigInteger.ONE, fastPrecision);
              overflowMant=overflowMant.subtract(BigInteger.ONE);
            }
            if (ctx.getHasFlags()) {
              ctx.setFlags(ctx.getFlags()|(flags));
            }
            clamp = FastInteger.Copy(fastEMax).Increment().Subtract(fastPrecision);
            return this.helper.CreateNewWithFlags(overflowMant, clamp.AsBigInteger(), neg ? BigNumberFlags.FlagNegative : 0);
          }
          if (ctx.getHasFlags()) {
            ctx.setFlags(ctx.getFlags()|(flags));
          }
          return this.SignalOverflow(neg);
        }
      }
      if (ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(flags));
      }
      if (ctx.getClampNormalExponents()) {
        // Clamp exponents to eMax + 1 - precision
        // if directed
        if (binaryPrec && this.thisRadix != 2) {
          fastPrecision = this.helper.CreateShiftAccumulator(maxMantissa).GetDigitLength();
        }
        FastInteger clampExp = FastInteger.Copy(fastEMax).Increment().Subtract(fastPrecision);
        if (exp.compareTo(clampExp) > 0) {
          if (bigmantissa.signum()!=0) {
            FastInteger expdiff = FastInteger.Copy(exp).Subtract(clampExp);
            bigmantissa = this.helper.MultiplyByRadixPower(bigmantissa, expdiff);
          }
          if (ctx.getHasFlags()) {
            ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagClamped));
          }
          exp = clampExp;
        }
      }
      return this.helper.CreateNewWithFlags(bigmantissa, exp.AsBigInteger(), neg ? BigNumberFlags.FlagNegative : 0);
    }

    // mant1 and mant2 are assumed to be nonnegative
    private T AddCore(BigInteger mant1, BigInteger mant2, BigInteger exponent, int flags1, int flags2, PrecisionContext ctx) {

      boolean neg1 = (flags1 & BigNumberFlags.FlagNegative) != 0;
      boolean neg2 = (flags2 & BigNumberFlags.FlagNegative) != 0;
      boolean negResult = false;
      if (neg1 != neg2) {
        // Signs are different, treat as a subtraction
        mant1=mant1.subtract(mant2);
        int mant1Sign = mant1.signum();
        negResult = neg1 ^ (mant1Sign == 0 ? neg2 : (mant1Sign < 0));
      } else {
        // Signs are same, treat as an addition
        mant1=mant1.add(mant2);
        negResult = neg1;
      }
      if (mant1.signum()==0 && negResult) {
        // Result is negative zero
        if (!((neg1 && neg2) || ((neg1 ^ neg2) && ctx != null && ctx.getRounding() == Rounding.Floor))) {
          negResult = false;
        }
      }
      return this.helper.CreateNewWithFlags(mant1, exponent, negResult ? BigNumberFlags.FlagNegative : 0);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param other A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Add(T thisValue, T other, PrecisionContext ctx) {
      int thisFlags = this.helper.GetFlags(thisValue);
      int otherFlags = this.helper.GetFlags(other);
      if (((thisFlags | otherFlags) & BigNumberFlags.FlagSpecial) != 0) {
        T result = this.HandleNotANumber(thisValue, other, ctx);
        if ((Object)result != (Object)null) {
          return result;
        }
        if ((thisFlags & BigNumberFlags.FlagInfinity) != 0) {
          if ((otherFlags & BigNumberFlags.FlagInfinity) != 0) {
            if ((thisFlags & BigNumberFlags.FlagNegative) != (otherFlags & BigNumberFlags.FlagNegative)) {
              return this.SignalInvalid(ctx);
            }
          }
          return thisValue;
        }
        if ((otherFlags & BigNumberFlags.FlagInfinity) != 0) {
          return other;
        }
      }
      int expcmp = this.helper.GetExponent(thisValue).compareTo((BigInteger)this.helper.GetExponent(other));
      T retval = null;
      BigInteger op1MantAbs = (this.helper.GetMantissa(thisValue)).abs();
      BigInteger op2MantAbs = (this.helper.GetMantissa(other)).abs();
      if (expcmp == 0) {
        retval = this.AddCore(op1MantAbs, op2MantAbs, this.helper.GetExponent(thisValue), thisFlags, otherFlags, ctx);
      } else {
        // choose the minimum exponent
        T op1 = thisValue;
        T op2 = other;
        BigInteger op1Exponent = this.helper.GetExponent(op1);
        BigInteger op2Exponent = this.helper.GetExponent(op2);
        BigInteger resultExponent = expcmp < 0 ? op1Exponent : op2Exponent;
        FastInteger fastOp1Exp = FastInteger.FromBig(op1Exponent);
        FastInteger fastOp2Exp = FastInteger.FromBig(op2Exponent);
        FastInteger expdiff = FastInteger.Copy(fastOp1Exp).Subtract(fastOp2Exp).Abs();
        if (ctx != null && ctx.getPrecision().signum() > 0) {
          // Check if exponent difference is too big for
          // radix-power calculation to work quickly
          FastInteger fastPrecision = FastInteger.FromBig(ctx.getPrecision());
          // If exponent difference is greater than the precision
          if (FastInteger.Copy(expdiff).compareTo(fastPrecision) > 0) {
            int expcmp2 = fastOp1Exp.compareTo(fastOp2Exp);
            if (expcmp2 < 0) {
              if (op2MantAbs.signum()!=0) {
                // first operand's exponent is less
                // and second operand isn't zero
                // second mantissa will be shifted by the exponent
                // difference
                // 111111111111|
                // 222222222222222|
                FastInteger digitLength1 = this.helper.CreateShiftAccumulator(op1MantAbs).GetDigitLength();
                if (FastInteger.Copy(fastOp1Exp).Add(digitLength1).AddInt(2).compareTo(fastOp2Exp) < 0) {
                  // first operand's mantissa can't reach the
                  // second operand's mantissa, so the exponent can be
                  // raised without affecting the result
                  FastInteger tmp = FastInteger.Copy(fastOp2Exp).SubtractInt(4).Subtract(digitLength1).SubtractBig(ctx.getPrecision());
                  FastInteger newDiff = FastInteger.Copy(tmp).Subtract(fastOp2Exp).Abs();
                  if (newDiff.compareTo(expdiff) < 0) {
                    // Can be treated as almost zero
                    boolean sameSign = this.helper.GetSign(thisValue) == this.helper.GetSign(other);
                    boolean oneOpIsZero = op1MantAbs.signum()==0;
                    FastInteger digitLength2 = this.helper.CreateShiftAccumulator(op2MantAbs).GetDigitLength();
                    if (digitLength2.compareTo(fastPrecision) < 0) {
                      // Second operand's precision too short
                      FastInteger precisionDiff = FastInteger.Copy(fastPrecision).Subtract(digitLength2);
                      if (!oneOpIsZero && !sameSign) {
                        precisionDiff.AddInt(2);
                      }
                      op2MantAbs = this.helper.MultiplyByRadixPower(op2MantAbs, precisionDiff);
                      BigInteger bigintTemp = precisionDiff.AsBigInteger();
                      op2Exponent=op2Exponent.subtract(bigintTemp);
                      if (!oneOpIsZero && !sameSign) {
                        op2MantAbs=op2MantAbs.subtract(BigInteger.ONE);
                      }
                      other = this.helper.CreateNewWithFlags(op2MantAbs, op2Exponent, this.helper.GetFlags(other));
                      FastInteger shift = FastInteger.Copy(digitLength2).Subtract(fastPrecision);
                      if (oneOpIsZero && ctx != null && ctx.getHasFlags()) {
                        ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagRounded));
                      }
                      return this.RoundToPrecisionWithShift(other, ctx, (oneOpIsZero || sameSign) ? 0 : 1, (oneOpIsZero && !sameSign) ? 0 : 1, shift, false);
                    } else {
                      if (!oneOpIsZero && !sameSign) {
                        op2MantAbs = this.helper.MultiplyByRadixPower(op2MantAbs, new FastInteger(2));
                        op2Exponent=op2Exponent.subtract(BigInteger.valueOf(2));
                        op2MantAbs=op2MantAbs.subtract(BigInteger.ONE);
                        other = this.helper.CreateNewWithFlags(op2MantAbs, op2Exponent, this.helper.GetFlags(other));
                        FastInteger shift = FastInteger.Copy(digitLength2).Subtract(fastPrecision);
                        return this.RoundToPrecisionWithShift(other, ctx, 0, 0, shift, false);
                      } else {
                        FastInteger shift2 = FastInteger.Copy(digitLength2).Subtract(fastPrecision);
                        if (!sameSign && ctx != null && ctx.getHasFlags()) {
                          ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagRounded));
                        }
                        return this.RoundToPrecisionWithShift(other, ctx, 0, sameSign ? 1 : 0, shift2, false);
                      }
                    }
                  }
                }
              }
            } else if (expcmp2 > 0) {
              if (op1MantAbs.signum()!=0) {
                // first operand's exponent is greater
                // and first operand isn't zero
                // first mantissa will be shifted by the exponent
                // difference
                // 111111111111|
                // 222222222222222|
                FastInteger digitLength2 = this.helper.CreateShiftAccumulator(op2MantAbs).GetDigitLength();
                if (FastInteger.Copy(fastOp2Exp).Add(digitLength2).AddInt(2).compareTo(fastOp1Exp) < 0) {
                  // second operand's mantissa can't reach the
                  // first operand's mantissa, so the exponent can be
                  // raised without affecting the result
                  FastInteger tmp = FastInteger.Copy(fastOp1Exp).SubtractInt(4).Subtract(digitLength2).SubtractBig(ctx.getPrecision());
                  FastInteger newDiff = FastInteger.Copy(tmp).Subtract(fastOp1Exp).Abs();
                  if (newDiff.compareTo(expdiff) < 0) {
                    // Can be treated as almost zero
                    boolean sameSign = this.helper.GetSign(thisValue) == this.helper.GetSign(other);
                    boolean oneOpIsZero = op2MantAbs.signum()==0;
                    digitLength2 = this.helper.CreateShiftAccumulator(op1MantAbs).GetDigitLength();
                    if (digitLength2.compareTo(fastPrecision) < 0) {
                      // Second operand's precision too short
                      FastInteger precisionDiff = FastInteger.Copy(fastPrecision).Subtract(digitLength2);
                      if (!oneOpIsZero && !sameSign) {
                        precisionDiff.AddInt(2);
                      }
                      op1MantAbs = this.helper.MultiplyByRadixPower(op1MantAbs, precisionDiff);
                      BigInteger bigintTemp = precisionDiff.AsBigInteger();
                      op1Exponent=op1Exponent.subtract(bigintTemp);
                      if (!oneOpIsZero && !sameSign) {
                        op1MantAbs=op1MantAbs.subtract(BigInteger.ONE);
                      }
                      thisValue = this.helper.CreateNewWithFlags(op1MantAbs, op1Exponent, this.helper.GetFlags(thisValue));
                      FastInteger shift = FastInteger.Copy(digitLength2).Subtract(fastPrecision);
                      if (oneOpIsZero && ctx != null && ctx.getHasFlags()) {
                        ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagRounded));
                      }
                      return this.RoundToPrecisionWithShift(thisValue, ctx, (oneOpIsZero || sameSign) ? 0 : 1, (oneOpIsZero && !sameSign) ? 0 : 1, shift, false);
                    } else {
                      if (!oneOpIsZero && !sameSign) {
                        op1MantAbs = this.helper.MultiplyByRadixPower(op1MantAbs, new FastInteger(2));
                        op1Exponent=op1Exponent.subtract(BigInteger.valueOf(2));
                        op1MantAbs=op1MantAbs.subtract(BigInteger.ONE);
                        thisValue = this.helper.CreateNewWithFlags(op1MantAbs, op1Exponent, this.helper.GetFlags(thisValue));
                        FastInteger shift = FastInteger.Copy(digitLength2).Subtract(fastPrecision);
                        return this.RoundToPrecisionWithShift(thisValue, ctx, 0, 0, shift, false);
                      } else {
                        FastInteger shift2 = FastInteger.Copy(digitLength2).Subtract(fastPrecision);
                        if (!sameSign && ctx != null && ctx.getHasFlags()) {
                          ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagRounded));
                        }
                        return this.RoundToPrecisionWithShift(thisValue, ctx, 0, sameSign ? 1 : 0, shift2, false);
                      }
                    }
                  }
                }
              }
            }
            expcmp = op1Exponent.compareTo(op2Exponent);
            resultExponent = expcmp < 0 ? op1Exponent : op2Exponent;
          }
        }
        if (expcmp > 0) {
          op1MantAbs = this.RescaleByExponentDiff(op1MantAbs, op1Exponent, op2Exponent);
          // System.out.println("{0} {1} -> {2}",op1MantAbs,op2MantAbs,op2MantAbs-op1MantAbs);
          retval = this.AddCore(op1MantAbs, op2MantAbs, resultExponent, thisFlags, otherFlags, ctx);
        } else {
          op2MantAbs = this.RescaleByExponentDiff(op2MantAbs, op1Exponent, op2Exponent);
          // System.out.println("{0} {1} -> {2}",op1MantAbs,op2MantAbs,op2MantAbs-op1MantAbs);
          retval = this.AddCore(op1MantAbs, op2MantAbs, resultExponent, thisFlags, otherFlags, ctx);
        }
      }
      if (ctx != null) {
        retval = this.RoundToPrecision(retval, ctx);
      }
      return retval;
    }

    /**
     * Compares a T object with this instance.
     * @param thisValue A T object. (2).
     * @param numberObject A T object. (3).
     * @param treatQuietNansAsSignaling A Boolean object.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T CompareToWithContext(T thisValue, T numberObject, boolean treatQuietNansAsSignaling, PrecisionContext ctx) {
      if (numberObject == null) {
        return this.SignalInvalid(ctx);
      }
      T result = this.CompareToHandleSpecial(thisValue, numberObject, treatQuietNansAsSignaling, ctx);
      if ((Object)result != (Object)null) {
        return result;
      }
      return this.ValueOf(this.compareTo(thisValue, numberObject), null);
    }

    /**
     * Compares a T object with this instance.
     * @param thisValue A T object.
     * @param numberObject A T object. (2).
     * @return Zero if the values are equal; a negative number if this instance
     * is less, or a positive number if this instance is greater.
     */
    public int compareTo(T thisValue, T numberObject) {
      if (numberObject == null) {
        return 1;
      }
      int flagsThis = this.helper.GetFlags(thisValue);
      int flagsOther = this.helper.GetFlags(numberObject);
      if ((flagsThis & BigNumberFlags.FlagNaN) != 0) {
        if ((flagsOther & BigNumberFlags.FlagNaN) != 0) {
          return 0;
        }
        return 1;
        // Treat NaN as greater
      }
      if ((flagsOther & BigNumberFlags.FlagNaN) != 0) {
        return -1;
        // Treat as less than NaN
      }
      int s = this.CompareToHandleSpecialReturnInt(thisValue, numberObject);
      if (s <= 1) {
        return s;
      }
      s = this.helper.GetSign(thisValue);
      int ds = this.helper.GetSign(numberObject);
      if (s != ds) {
        return (s < ds) ? -1 : 1;
      }
      if (ds == 0 || s == 0) {
        // Special case: Either operand is zero
        return 0;
      }
      int expcmp = this.helper.GetExponent(thisValue).compareTo((BigInteger)this.helper.GetExponent(numberObject));
      // At this point, the signs are equal so we can compare
      // their absolute values instead
      int mantcmp = (this.helper.GetMantissa(thisValue)).abs().compareTo((this.helper.GetMantissa(numberObject)).abs());
      if (s < 0) {
        mantcmp = -mantcmp;
      }
      if (mantcmp == 0) {
        // Special case: Mantissas are equal
        return s < 0 ? -expcmp : expcmp;
      }
      if (expcmp == 0) {
        return mantcmp;
      }
      BigInteger op1Exponent = this.helper.GetExponent(thisValue);
      BigInteger op2Exponent = this.helper.GetExponent(numberObject);
      FastInteger fastOp1Exp = FastInteger.FromBig(op1Exponent);
      FastInteger fastOp2Exp = FastInteger.FromBig(op2Exponent);
      FastInteger expdiff = FastInteger.Copy(fastOp1Exp).Subtract(fastOp2Exp).Abs();
      // Check if exponent difference is too big for
      // radix-power calculation to work quickly
      if (expdiff.CompareToInt(100) >= 0) {
        BigInteger op1MantAbs = (this.helper.GetMantissa(thisValue)).abs();
        BigInteger op2MantAbs = (this.helper.GetMantissa(numberObject)).abs();
        FastInteger precision1 = this.helper.CreateShiftAccumulator(op1MantAbs).GetDigitLength();
        FastInteger precision2 = this.helper.CreateShiftAccumulator(op2MantAbs).GetDigitLength();
        FastInteger maxPrecision = null;
        if (precision1.compareTo(precision2) > 0) {
          maxPrecision = precision1;
        } else {
          maxPrecision = precision2;
        }
        // If exponent difference is greater than the
        // maximum precision of the two operands
        if (FastInteger.Copy(expdiff).compareTo(maxPrecision) > 0) {
          int expcmp2 = fastOp1Exp.compareTo(fastOp2Exp);
          if (expcmp2 < 0) {
            if (op2MantAbs.signum()!=0) {
              // first operand's exponent is less
              // and second operand isn't zero
              // second mantissa will be shifted by the exponent
              // difference
              // 111111111111|
              // 222222222222222|
              FastInteger digitLength1 = this.helper.CreateShiftAccumulator(op1MantAbs).GetDigitLength();
              if (FastInteger.Copy(fastOp1Exp).Add(digitLength1).AddInt(2).compareTo(fastOp2Exp) < 0) {
                // first operand's mantissa can't reach the
                // second operand's mantissa, so the exponent can be
                // raised without affecting the result
                FastInteger tmp = FastInteger.Copy(fastOp2Exp).SubtractInt(8).Subtract(digitLength1).Subtract(maxPrecision);
                FastInteger newDiff = FastInteger.Copy(tmp).Subtract(fastOp2Exp).Abs();
                if (newDiff.compareTo(expdiff) < 0) {
                  if (s == ds) {
                    return (s < 0) ? 1 : -1;
                  } else {
                    op1Exponent = tmp.AsBigInteger();
                  }
                }
              }
            }
          } else if (expcmp2 > 0) {
            if (op1MantAbs.signum()!=0) {
              // first operand's exponent is greater
              // and second operand isn't zero
              // first mantissa will be shifted by the exponent
              // difference
              // 111111111111|
              // 222222222222222|
              FastInteger digitLength2 = this.helper.CreateShiftAccumulator(op2MantAbs).GetDigitLength();
              if (FastInteger.Copy(fastOp2Exp).Add(digitLength2).AddInt(2).compareTo(fastOp1Exp) < 0) {
                // second operand's mantissa can't reach the
                // first operand's mantissa, so the exponent can be
                // raised without affecting the result
                FastInteger tmp = FastInteger.Copy(fastOp1Exp).SubtractInt(8).Subtract(digitLength2).Subtract(maxPrecision);
                FastInteger newDiff = FastInteger.Copy(tmp).Subtract(fastOp1Exp).Abs();
                if (newDiff.compareTo(expdiff) < 0) {
                  if (s == ds) {
                    return (s < 0) ? -1 : 1;
                  } else {
                    op2Exponent = tmp.AsBigInteger();
                  }
                }
              }
            }
          }
          expcmp = op1Exponent.compareTo(op2Exponent);
        }
      }
      if (expcmp > 0) {
        BigInteger newmant = this.RescaleByExponentDiff(this.helper.GetMantissa(thisValue), op1Exponent, op2Exponent);
        BigInteger othermant = (this.helper.GetMantissa(numberObject)).abs();
        newmant = (newmant).abs();
        mantcmp = newmant.compareTo(othermant);
        return (s < 0) ? -mantcmp : mantcmp;
      } else {
        BigInteger newmant = this.RescaleByExponentDiff(this.helper.GetMantissa(numberObject), op1Exponent, op2Exponent);
        BigInteger othermant = (this.helper.GetMantissa(thisValue)).abs();
        newmant = (newmant).abs();
        mantcmp = othermant.compareTo(newmant);
        return (s < 0) ? -mantcmp : mantcmp;
      }
    }
  }

