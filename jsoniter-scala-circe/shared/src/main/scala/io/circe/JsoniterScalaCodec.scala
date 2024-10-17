package io.circe

import com.github.plokhotnyuk.jsoniter_scala.core._
import io.circe.Decoder.Result
import io.circe.Json._
import java.nio.charset.StandardCharsets
import java.util
import scala.collection.immutable.VectorBuilder
import scala.util.control.NonFatal

object JsoniterScalaCodec {
  /**
   * Default number parser that detects integers vs floating-point values
   * and chooses an appropriate JSON number representation.
   *
   * @return a JSON number value
   */
  val defaultNumberParser: JsonReader => Json = in => new JNumber({
    in.setMark()
    var b = in.nextByte()
    if (b == '-') b = in.nextByte()
    var digits = 0
    while ((b >= '0' && b <= '9') && {
      digits += 1
      in.hasRemaining()
    }) b = in.nextByte()
    in.rollbackToMark()
    if ((b | 0x20) != 'e' && b != '.') {
      if (digits < 10) new JsonLong(in.readInt())
      else if (digits < 19) new JsonLong(in.readLong())
      else {
        val x = in.readBigInt(null)
        if (x.isValidLong) new JsonLong(x.longValue)
        else new JsonBigDecimal(new java.math.BigDecimal(x.bigInteger))
      }
    } else new JsonBigDecimal(in.readBigDecimal(null).bigDecimal)
  })

  val defaultNumberSerializer: (JsonWriter, JsonNumber) => Unit = (out: JsonWriter, x: JsonNumber) => x match {
    case l: JsonLong => out.writeVal(l.value)
    case f: JsonFloat => out.writeVal(f.value)
    case d: JsonDouble => out.writeVal(d.value)
    case bd: JsonBigDecimal => out.writeVal(bd.value)
    case _ => out.writeRawVal(x.toString.getBytes(StandardCharsets.UTF_8))
  }

  val jsCompatibleNumberSerializer: (JsonWriter, JsonNumber) => Unit = (out: JsonWriter, x: JsonNumber) => x match {
    case l: JsonLong =>
      val v = l.value
      if (v >= -4503599627370496L && v < 4503599627370496L) out.writeVal(v)
      else out.writeValAsString(v)
    case f: JsonFloat => out.writeVal(f.value)
    case d: JsonDouble => out.writeVal(d.value)
    case bd: JsonBigDecimal =>
      val v = bd.value
      val bl = v.unscaledValue.bitLength
      val s = v.scale
      if (bl <= 52 && s >= -256 && s <= 256) out.writeVal(v)
      else out.writeValAsString(v)
    case _ => x.toBigDecimal match {
      case Some(bd) =>
        val u = bd.bigDecimal
        val bl = u.unscaledValue.bitLength
        val s = u.scale
        if (bl <= 52 && s >= -256 && s <= 256) out.writeVal(u)
        else out.writeValAsString(u)
      case _ =>
        out.writeNonEscapedAsciiVal(x.toString)
    }
  }

  /**
   * Converts an ASCII byte array to a JSON string.
   *
   * @param buf the ASCII byte array
   * @param len the length of the byte array
   * @return a JSON string
   */
  @inline
  def asciiStringToJString(buf: Array[Byte], len: Int): Json = new JString(StringUtil.toString(buf, len))

  /**
   * Extracts a `String` value from a JSON cursor.
   *
   * @param c the JSON cursor
   * @return the `String` value, or null if the cursor does not point to a string
   */
  @inline
  def stringValue(c: HCursor): String = c.value match {
    case s: JString => s.value
    case _ => null
  }

  def byteCodec(fromString: String => Byte): Codec[Byte] = new Codec[Byte] {
    final def apply(c: HCursor): Result[Byte] = c.value match {
      case n: JNumber =>
        n.value match {
          case jl: JsonLong =>
            val l = jl.value
            val b = l.toByte
            if (b == l) return new Right(b)
          case y =>
            val ol = y.toLong
            if (ol ne None) {
              val l = ol.get
              val b = l.toByte
              if (b == l) return new Right(b)
            }
        }
        fail(c)
      case s: JString => try new Right(fromString(s.value)) catch {
        case NonFatal(_) => fail(c)
      }
      case _ => fail(c)
    }

    final def apply(x: Byte): Json = new JNumber(new JsonLong(x))

    private[this] def fail(c: HCursor): Result[Byte] = new Left(DecodingFailure("Byte", c.history))
  }
  def shortCodec(fromString: String => Short): Codec[Short] = new Codec[Short] {
    final def apply(c: HCursor): Result[Short] = c.value match {
      case n: JNumber =>
        n.value match {
          case jl: JsonLong =>
            val l = jl.value
            val s = l.toShort
            if (s == l) return new Right(s)
          case y =>
            val ol = y.toLong
            if (ol ne None) {
              val l = ol.get
              val s = l.toShort
              if (s == l) return new Right(s)
            }
        }
        fail(c)
      case s: JString => try new Right(fromString(s.value)) catch {
        case NonFatal(_) => fail(c)
      }
      case _ => fail(c)
    }

    final def apply(x: Short): Json = new JNumber(new JsonLong(x))

    private[this] def fail(c: HCursor): Result[Short] = new Left(DecodingFailure("Short", c.history))
  }
  def intCodec(fromString: String => Int): Codec[Int] = new Codec[Int] {
    final def apply(c: HCursor): Result[Int] = c.value match {
      case n: JNumber =>
        n.value match {
          case jl: JsonLong =>
            val l = jl.value
            val i = l.toInt
            if (i == l) return new Right(i)
          case y =>
            val ol = y.toLong
            if (ol ne None) {
              val l = ol.get
              val i = l.toInt
              if (i == l) return new Right(i)
            }
        }
        fail(c)
      case s: JString => try new Right(fromString(s.value)) catch {
        case NonFatal(_) => fail(c)
      }
      case _ => fail(c)
    }

    final def apply(x: Int): Json = new JNumber(new JsonLong(x))

    private[this] def fail(c: HCursor): Result[Int] = new Left(DecodingFailure("Int", c.history))
  }
  def longCodec(fromString: String => Long): Codec[Long] = new Codec[Long] {
    final def apply(c: HCursor): Result[Long] = c.value match {
      case n: JNumber => n.value match {
        case jl: JsonLong => new Right(jl.value)
        case x =>
          val ol = x.toLong
          if (ol ne None) new Right(ol.get)
          else fail(c)
      }
      case s: JString => try new Right(fromString(s.value)) catch {
        case NonFatal(_) => fail(c)
      }
      case _ => fail(c)
    }

    final def apply(x: Long): Json = new JNumber(new JsonLong(x))

    private[this] def fail(c: HCursor): Result[Long] = new Left(DecodingFailure("Long", c.history))
  }
  def floatCodec(fromString: String => Float): Codec[Float] = new Codec[Float] {
    final def apply(c: HCursor): Result[Float] = c.value match {
      case n: JNumber => new Right(n.value.toFloat)
      case s: JString => try new Right(fromString(s.value)) catch {
        case NonFatal(_) => fail(c)
      }
      case _ => fail(c)
    }

    final def apply(x: Float): Json = new JNumber(new JsonFloat(x))

    private[this] def fail(c: HCursor): Result[Float] = new Left(DecodingFailure("Float", c.history))
  }
  def doubleCodec(fromString: String => Double): Codec[Double] = new Codec[Double] {
    final def apply(c: HCursor): Result[Double] = c.value match {
      case n: JNumber => new Right(n.value.toDouble)
      case s: JString => try new Right(fromString(s.value)) catch {
        case NonFatal(_) => fail(c)
      }
      case _ => fail(c)
    }

    final def apply(x: Double): Json = new JNumber(new JsonDouble(x))

    private[this] def fail(c: HCursor): Result[Double] = new Left(DecodingFailure("Double", c.history))
  }
  def bigIntCodec(fromString: String => BigInt): Codec[BigInt] = new Codec[BigInt] {
    final def apply(c: HCursor): Result[BigInt] = try c.value match {
      case n: JNumber => n.value match {
        case jl: JsonLong => new Right(BigInt(jl.value))
        case jbd: JsonBigDecimal => new Right(new BigInt({
          val bd = jbd.value
          if (bd.scale == 0) bd.unscaledValue
          else bd.toBigIntegerExact
        }))
        case x =>
          val obi = x.toBigInt
          if (obi ne None) new Right(obi.get)
          else fail(c)
      }
      case s: JString => new Right(fromString(s.value))
      case _ => fail(c)
    } catch {
      case NonFatal(_) => fail(c)
    }

    final def apply(x: BigInt): Json = new JNumber({
      if (x.isValidLong) new JsonLong(x.longValue)
      else new JsonBigDecimal(new java.math.BigDecimal(x.bigInteger))
    })

    private[this] def fail(c: HCursor): Result[BigInt] = new Left(DecodingFailure("BigInt", c.history))
  }
  def bigDecimalCodec(fromString: String => BigDecimal): Codec[BigDecimal] = new Codec[BigDecimal] {
    final def apply(c: HCursor): Result[BigDecimal] = c.value match {
      case n: JNumber => n.value match {
        case jl: JsonLong => new Right(new BigDecimal(new java.math.BigDecimal(jl.value)))
        case jbd: JsonBigDecimal => new Right(new BigDecimal(jbd.value, JsonReader.bigDecimalMathContext))
        case x => x.toBigDecimal match {
          case Some(v) => new Right(v.apply(JsonReader.bigDecimalMathContext))
          case _ => fail(c)
        }
      }
      case s: JString => try new Right(fromString(s.value)) catch {
        case NonFatal(_) => fail(c)
      }
      case _ => fail(c)
    }

    final def apply(x: BigDecimal): Json = new JNumber(new JsonBigDecimal(x.bigDecimal))

    private[this] def fail(c: HCursor): Result[BigDecimal] = new Left(DecodingFailure("BigDecimal", c.history))
  }
}

/**
 * A JSON value codec that parses and serialize to/from circe's JSON AST.
 *
 * @param maxDepth the maximum depth for decoding
 * @param initialSize the initial size hint for object and array collections
 * @param doSerialize a predicate that determines whether a value should be serialized
 * @param numberParser a function that parses JSON numbers
 * @param numberSerializer a function that serializes JSON numbers
 * @return The JSON codec
 */
final class JsoniterScalaCodec(
                                maxDepth: Int,
                                initialSize: Int,
                                doSerialize: Json => Boolean,
                                numberParser: JsonReader => Json,
                                numberSerializer: (JsonWriter, JsonNumber) => Unit) extends JsonValueCodec[Json] {

  /**
   * An auxiliary constructor for backward binary compatibility.
   *
   * @param maxDepth the maximum depth for decoding
   * @param initialSize the initial size hint for object and array collections
   * @param doSerialize a predicate that determines whether a value should be serialized
   * @param numberParser a function that parses JSON numbers
   */
  def this(maxDepth: Int, initialSize: Int, doSerialize: Json => Boolean, numberParser: JsonReader => Json) =
    this(maxDepth, initialSize, doSerialize, numberParser, JsoniterScalaCodec.defaultNumberSerializer)

  private[this] val trueValue = True
  private[this] val falseValue = False
  private[this] val emptyArrayValue = new JArray(Vector.empty)
  private[this] val emptyObjectValue = new JObject(JsonObject.empty)

  override val nullValue: Json = JNull

  @inline
  override def decodeValue(in: JsonReader, default: Json): Json = decode(in, maxDepth)

  @inline
  override def encodeValue(x: Json, out: JsonWriter): Unit = encode(x, out, maxDepth)

  private[this] def decode(in: JsonReader, depth: Int): Json = {
    val b = in.nextToken()
    if (b == '"') {
      in.rollbackToken()
      new JString(in.readString(null))
    } else if (b == 'f' || b == 't') {
      in.rollbackToken()
      if (in.readBoolean()) trueValue
      else falseValue
    } else if (b >= '0' && b <= '9' || b == '-') {
      in.rollbackToken()
      numberParser(in)
    } else if (b == '[') {
      val depthM1 = depth - 1
      if (depthM1 < 0) in.decodeError("depth limit exceeded")
      if (in.isNextToken(']')) emptyArrayValue
      else {
        in.rollbackToken()
        val x = new VectorBuilder[Json]
        while ({
          x += decode(in, depthM1)
          in.isNextToken(',')
        }) ()
        if (in.isCurrentToken(']')) new JArray(x.result())
        else in.arrayEndOrCommaError()
      }
    } else if (b == '{') {
      val depthM1 = depth - 1
      if (depthM1 < 0) in.decodeError("depth limit exceeded")
      if (in.isNextToken('}')) emptyObjectValue
      else {
        in.rollbackToken()
        val x = new util.LinkedHashMap[String, Json](initialSize, 0.75f)
        while ({
          x.put(in.readKeyAsString(), decode(in, depthM1))
          in.isNextToken(',')
        }) ()
        if (in.isCurrentToken('}')) new JObject(JsonObject.fromLinkedHashMap(x))
        else in.objectEndOrCommaError()
      }
    } else in.readNullOrError(nullValue, "expected JSON value")
  }

  private[this] def encode(x: Json, out: JsonWriter, depth: Int): Unit = x match {
    case s: JString => out.writeVal(s.value)
    case b: JBoolean => out.writeVal(b.value)
    case n: JNumber => numberSerializer(out, n.value)
    case a: JArray =>
      val depthM1 = depth - 1
      if (depthM1 < 0) out.encodeError("depth limit exceeded")
      out.writeArrayStart()
      a.value.foreach(v => encode(v, out, depthM1))
      out.writeArrayEnd()
    case o: JObject =>
      val depthM1 = depth - 1
      if (depthM1 < 0) out.encodeError("depth limit exceeded")
      out.writeObjectStart()
      val it = o.value.toIterable.iterator
      while (it.hasNext) {
        val kv = it.next()
        val v = kv._2
        if (doSerialize(v)) {
          out.writeKey(kv._1)
          encode(v, out, depthM1)
        }
      }
      out.writeObjectEnd()
    case _ => out.writeNull()
  }
}