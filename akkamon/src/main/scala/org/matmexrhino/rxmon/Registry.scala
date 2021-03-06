/*
 * Copyright 2013-2014 Eugene Vigdorchik.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.maxmexrhino.rxmon

import org.maxmexrhino.rxmon.util.ReceiveBoxed
import scala.reflect.ClassTag
import akka.actor.{ Actor, ActorRef, Props }
import rx.lang.scala.{ Observable, Observer, Subscription }

case object ListEntries
case class EntriesResponse(entries: Map[String, ActorRef])

/** Subclass this actor class and register observables you want to collect.
 *  Then each actor that wants to send statistics needs to send ListEntries
 *  and send statistics to the actor it identifies from a map it gets with
 *  EntriesResponse. In addition local batching is supported, so that a proxy
 *  may batch local statistics.
 */
abstract class Registry extends Actor {
  private var monitors = Map.empty[String, ActorRef]

  def receive = {
    case ListEntries => sender ! EntriesResponse(monitors)
  }

  protected def register[T: ClassTag](name: String): Observable[T] =
    Observable { observer =>
      val tag = implicitly[ClassTag[T]]
      val monitor = context.actorOf(Props(new Monitor[T](observer, tag)), name)
      monitors = monitors + (name -> monitor)
      Subscription {
        context stop monitor
        monitors = monitors - name
      }
    }

  private class Monitor[T](observer: Observer[T], val ct: ClassTag[T]) extends Actor with ReceiveBoxed[T] {
    def receive: Receive = {
      case boxedTag(v) => observer onNext v.asInstanceOf[T]
      case x => observer onError new Exception(s"Unknown value received of class ${x.getClass}")
    }
  }
}
