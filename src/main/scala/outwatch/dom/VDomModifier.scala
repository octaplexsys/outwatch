package outwatch.dom

import cats.effect.IO
import org.scalajs.dom._
import outwatch.dom.helpers.DomUtils
import rxscalajs.Observer
import snabbdom.{DataObject, VNodeProxy, hFunction}

import scala.scalajs.js
import collection.breakOut


/*
VDomModifier_
  Property
    Attribute
      Attr
      AccumAttr
      Prop
      Style
      EmptyAttribute
    Hook
      InsertHook
      PrePatchHook
      UpdateHook
      PostPatchHook
      DestroyHook
    Key
  ChildVNode
    StaticVNode
      StringVNode
      VTree
    ChildStreamReceiver
    ChildrenStreamReceiver
  Emitter
  AttributeStreamReceiver
  CompositeModifier
  StringModifier
  EmptyModifier
 */


sealed trait VDomModifier_ extends Any

// Modifiers

sealed trait Property extends VDomModifier_

final case class Emitter(eventType: String, trigger: Event => Unit) extends VDomModifier_

private[outwatch] final case class AttributeStreamReceiver(attribute: String, attributeStream: Observable[Attribute]) extends VDomModifier_

private[outwatch] final case class CompositeModifier(modifiers: Seq[VDomModifier]) extends VDomModifier_

case object EmptyModifier extends VDomModifier_

private[outwatch] final case class StringModifier(string: String) extends VDomModifier_

sealed trait ChildVNode extends Any with VDomModifier_

// Properties

final case class Key(value: Key.Value) extends Property
object Key {
  type Value = DataObject.KeyValue
}

sealed trait Attribute extends Property {
  val title: String
}
object Attribute {
  def apply(title: String, value: Attr.Value) = Attr(title, value)
}


sealed trait Hook[T] extends Property {
  def observer: Observer[T]
}

// Attributes

case object EmptyAttribute extends Attribute {
  val title: String = ""
}

final case class Attr(title: String, value: Attr.Value) extends Attribute
object Attr {
  type Value = DataObject.AttrValue
}

final case class Prop(title: String, value: Prop.Value) extends Attribute
object Prop {
  type Value = DataObject.PropValue
}

final case class Style(title: String, value: String) extends Attribute

// Hooks

private[outwatch] final case class InsertHook(observer: Observer[Element]) extends Hook[Element]
private[outwatch] final case class PrePatchHook(observer: Observer[(Option[Element], Option[Element])])
  extends Hook[(Option[Element], Option[Element])]
private[outwatch] final case class UpdateHook(observer: Observer[(Element, Element)]) extends Hook[(Element, Element)]
private[outwatch] final case class PostPatchHook(observer: Observer[(Element, Element)]) extends Hook[(Element, Element)]
private[outwatch] final case class DestroyHook(observer: Observer[Element]) extends Hook[Element]

// Child Nodes
sealed trait StaticVNode extends Any with ChildVNode {
  def asProxy: VNodeProxy
}

final case class ChildStreamReceiver(childStream: Observable[IO[StaticVNode]]) extends ChildVNode
final case class ChildrenStreamReceiver(childrenStream: Observable[Seq[IO[StaticVNode]]]) extends ChildVNode

// Static Nodes
private[outwatch] final case class StringVNode(string: String) extends AnyVal with StaticVNode {
  override def asProxy: VNodeProxy = VNodeProxy.fromString(string)
}

// TODO: instead of Seq[VDomModifier] use Vector or JSArray?
// Fast concatenation and lastOption operations are important
// Needs to be benchmarked in the Browser
private[outwatch] final case class VTree(nodeType: String,
                       modifiers: Seq[VDomModifier]) extends StaticVNode {

  def apply(args: VDomModifier*) = IO.pure(VTree(nodeType, modifiers ++ args))

  override def asProxy: VNodeProxy = {
    val modifiers_ = modifiers.map(_.unsafeRunSync())
    val (children, attributeObject, hasChildVNodes, textChildren) = DomUtils.extractChildrenAndDataObject(modifiers_)
    //TODO: use .sequence instead of unsafeRunSync?
    // import cats.instances.list._
    // import cats.syntax.traverse._
    // for { childProxies <- children.map(_.value).sequence }
    // yield hFunction(nodeType, attributeObject, childProxies.map(_.apsProxy)(breakOut))
    if (hasChildVNodes) { // children.nonEmpty doesn't work, children will always include StringModifiers as StringNodes
      val childProxies: js.Array[VNodeProxy] = children.map(_.asProxy)(breakOut)
      hFunction(nodeType, attributeObject, childProxies)
    }
    else if (textChildren.nonEmpty) {
      hFunction(nodeType, attributeObject, textChildren.map(_.string).mkString)
    }
    else {
      hFunction(nodeType, attributeObject)
    }
  }
}






