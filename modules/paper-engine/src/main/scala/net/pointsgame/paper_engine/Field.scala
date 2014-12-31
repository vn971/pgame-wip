package net.pointsgame.paper_engine

import scala.annotation.tailrec

final class Field(private val vector: Vector2D[PosValue], val scoreRed: Int, val scoreBlack: Int, val moves: List[ColoredPos], val lastSurroundChain: Option[ColoredChain]) {
  def width: Int =
    vector.width
  def height: Int =
    vector.height
  def lastPlayer: Option[Player] =
    moves.headOption.map(_.player)
  private def apply(pos: Pos): PosValue =
    vector(pos.x, pos.y)
  def isInField(pos: Pos): Boolean =
    pos.x >= 0 && pos.x < width && pos.y >= 0 && pos.y < height
  def isPuttingAllowed(pos: Pos): Boolean =
    isInField(pos) && apply(pos).isFree
  /** Returns `true` if the player controls the Pos (same color or has surrounded the Pos) */
  def isPlayersPoint(pos: Pos, player: Player): Boolean =
    isInField(pos) && apply(pos).isPlayer(player)
  private def getFirstNextPos(centerPos: Pos, pos: Pos): Pos = (pos.dx(centerPos), pos.dy(centerPos)) match {
    case (-1, -1) => centerPos.se
    case (0, -1)  => centerPos.ne
    case (1, -1)  => centerPos.ne
    case (-1, 0)  => centerPos.se
    case (0, 0)   => centerPos.se
    case (1, 0)   => centerPos.nw
    case (-1, 1)  => centerPos.sw
    case (0, 1)   => centerPos.sw
    case (1, 1)   => centerPos.nw
    case _        => throw new IllegalArgumentException(s"getFirstNextPos: not adjacent points: $centerPos and $pos.")
  }
  private def getNextPos(centerPos: Pos, pos: Pos): Pos = (pos.dx(centerPos), pos.dy(centerPos)) match {
    case (-1, -1) => pos.e
    case (0, -1)  => pos.e
    case (1, -1)  => pos.n
    case (-1, 0)  => pos.s
    case (0, 0)   => pos.s
    case (1, 0)   => pos.n
    case (-1, 1)  => pos.s
    case (0, 1)   => pos.w
    case (1, 1)   => pos.w
    case _        => throw new IllegalArgumentException(s"getNextPos: not adjacent points: $centerPos and $pos.")
  }
  private def fiberBundle(pos1: Pos, pos2: Pos): Int =
    pos1.x * pos2.y - pos2.x * pos1.y
  private def square(chain: List[Pos]): Int = {
    @tailrec
    def _square(l: List[Pos], acc: Int): Int = l match {
      case List(a) => acc + fiberBundle(a, chain.head)
      case h :: t  => _square(t, acc + fiberBundle(h, t.head))
      case _       => throw new IllegalStateException("Square: bug.")
    }
    _square(chain, 0)
  }
  private def buildChain(startPos: Pos, nextPos: Pos, player: Player): Option[List[Pos]] = {
    @tailrec
    def getNextPlayerPos(centerPos: Pos, pos: Pos): Pos =
      if (pos == startPos)
        pos
      else if (isPlayersPoint(pos, player))
        pos
      else
        getNextPlayerPos(centerPos, getNextPos(centerPos, pos))
    @tailrec
    def getChain(start: Pos, list: List[Pos]): List[Pos] = {
      val h = list.head
      val nextPos = getNextPlayerPos(h, getFirstNextPos(h, start))
      if (nextPos == startPos)
        list
      else if (list.contains(nextPos))
        getChain(h, list.dropWhile(_ != nextPos))
      else
        getChain(h, nextPos :: list)
    }
    val chain = getChain(startPos, List(nextPos, startPos))
    if (chain.length > 2 && square(chain) > 0)
      Some(chain)
    else
      None
  }
  private def getInputPoints(pos: Pos, player: Player): List[(Pos, Pos)] = {
    val list1 =
      if (!isPlayersPoint(pos.w, player)) {
        if (isPlayersPoint(pos.sw, player))
          (pos.sw, pos.w) :: Nil
        else if (isPlayersPoint(pos.s, player))
          (pos.s, pos.w) :: Nil
        else
          Nil
      } else {
        Nil
      }
    val list2 =
      if (!isPlayersPoint(pos.n, player)) {
        if (isPlayersPoint(pos.nw, player))
          (pos.nw, pos.n) :: list1
        else if (isPlayersPoint(pos.w, player))
          (pos.w, pos.n) :: list1
        else
          list1
      } else {
        list1
      }
    val list3 =
      if (!isPlayersPoint(pos.e, player)) {
        if (isPlayersPoint(pos.ne, player))
          (pos.ne, pos.e) :: list2
        else if (isPlayersPoint(pos.n, player))
          (pos.n, pos.e) :: list2
        else
          list2
      } else {
        list2
      }
    val list4 =
      if (!isPlayersPoint(pos.s, player)) {
        if (isPlayersPoint(pos.se, player))
          (pos.se, pos.s) :: list3
        else if (isPlayersPoint(pos.e, player))
          (pos.e, pos.s) :: list3
        else
          list3
      } else {
        list3
      }
    list4
  }
  private def isPosInsideRing(pos: Pos, ring: List[Pos]): Boolean = {
    def removeNearSame(list: List[Int]): List[Int] =
      list.foldRight(List(list.last))((a, acc) => if (acc.head == a) acc else a :: acc)
    val _ring = removeNearSame(ring.filter(_.x <= pos.x).map(_.y))
    val __ring =
      if (_ring.last == pos.y)
        _ring :+ (if (_ring.head == pos.y) _ring.tail else _ring).head
      else if (_ring.head == pos.y)
        _ring.last :: _ring
      else
        _ring
    (__ring, __ring.tail, __ring.tail.tail).zipped.count {
      case (a, b, c) => b == pos.y && ((a < b && c > b) || (a > b && c < b))
    } % 2 == 1
  }
  def wave(startPos: Pos, f: Pos => Boolean): List[Pos] = {
    def neighborhood(pos: Pos): List[Pos] =
      List(pos.n, pos.s, pos.w, pos.e)
    def nextFront(passed: Set[Pos], front: Set[Pos]): Set[Pos] =
      front.flatMap(neighborhood).filter(isInField).diff(passed).filter(f)
    @tailrec
    def _wave(passed: Set[Pos], front: Set[Pos]): List[Pos] =
      if (front.isEmpty)
        passed.toList
      else
        _wave(passed.union(front), nextFront(passed, front))
    _wave(Set.empty, Set(startPos))
  }
  private def getInsideRing(startPos: Pos, ring: List[Pos]): List[Pos] = {
    val ringSet = ring.toSet
    wave(startPos, !ringSet.contains(_))
  }
  private def getEmptyBase(startPos: Pos, player: Player): (List[Pos], List[Pos]) = {
    @tailrec
    def getEmptyBaseChain(pos: Pos): List[Pos] =
      if (!isPlayersPoint(pos, player)) {
        getEmptyBaseChain(pos.w)
      } else {
        val inputPoints = getInputPoints(pos, player)
        val chains = inputPoints.flatMap { case (chainPos, _) => buildChain(pos, chainPos, player) }
        chains.find(isPosInsideRing(startPos, _)) match {
          case Some(result) => result
          case None         => getEmptyBaseChain(pos.w)
        }
      }
    val emptyBaseChain = getEmptyBaseChain(startPos.w)
    (emptyBaseChain, getInsideRing(startPos, emptyBaseChain).filter(apply(_).isFree))
  }
  def putPoint(pos: Pos, player: Player): Field = {
    require(isPuttingAllowed(pos), s"Field: putting not allowed at $pos.")
    val enemy = player.next
    val value = apply(pos)
    if (value.isEmptyBase(player)) {
      new Field(vector.updated(pos.x, pos.y, PlayerPosValue(player)), scoreRed, scoreBlack, ColoredPos(pos, player) :: moves, None)
    } else {
      val captures = getInputPoints(pos, player).flatMap {
        case (chainPos, capturedPos) =>
          for {
            chain <- buildChain(pos, chainPos, player)
            captured = getInsideRing(capturedPos, chain)
            capturedCount = captured.count(isPlayersPoint(_, enemy))
          } yield (chain, captured, capturedCount)
      }
      val (realCaptures, emptyCaptures) = captures.partition(_._3 != 0)
      val deltaScore = realCaptures.map(_._3).sum
      val realCaptured = realCaptures.flatMap(_._2)
      val captureChain = realCaptures.flatMap(_._1.reverse)
      if (value.isEmptyBase(enemy)) {
        val (enemyEmptyBaseChain, enemyEmptyBase) = getEmptyBase(pos, enemy)
        if (captures.nonEmpty) {
          val newScoreRed = if (player == Player.Red) scoreRed + deltaScore else scoreRed
          val newScoreBlack = if (player == Player.Black) scoreBlack + deltaScore else scoreBlack
          val updatedVector1 = enemyEmptyBase.foldLeft(vector)((acc, p) => acc.updated(p.x, p.y, EmptyPosValue))
          val updatedVector2 = updatedVector1.updated(pos.x, pos.y, PlayerPosValue(player))
          val updatedVector3 = realCaptured.foldLeft(updatedVector2)((acc, p) => acc.updated(p.x, p.y, PlayerPosValue(player)))
          new Field(updatedVector3, newScoreRed, newScoreBlack, ColoredPos(pos, player) :: moves, Some(ColoredChain(captureChain, player)))
        } else {
          val newScoreRed = if (player == Player.Red) scoreRed else scoreRed + 1
          val newScoreBlack = if (player == Player.Black) scoreBlack else scoreBlack + 1
          val updatedVector = enemyEmptyBase.foldLeft(vector)((acc, p) => acc.updated(p.x, p.y, PlayerPosValue(enemy)))
          new Field(updatedVector, newScoreRed, newScoreBlack, ColoredPos(pos, player) :: moves, Some(ColoredChain(enemyEmptyBaseChain, enemy)))
        }
      } else {
        val newEmptyBase = emptyCaptures.flatMap(_._2)
        val newScoreRed = if (player == Player.Red) scoreRed + deltaScore else scoreRed
        val newScoreBlack = if (player == Player.Black) scoreBlack + deltaScore else scoreBlack
        val updatedVector1 = vector.updated(pos.x, pos.y, PlayerPosValue(player))
        val updatedVector2 = realCaptured.foldLeft(updatedVector1)((acc, p) => acc.updated(p.x, p.y, PlayerPosValue(player)))
        val updatedVector3 = newEmptyBase.foldLeft(updatedVector2)((acc, p) => acc.updated(p.x, p.y, EmptyBasePosValue(player)))
        new Field(updatedVector3, newScoreRed, newScoreBlack, ColoredPos(pos, player) :: moves, if (captureChain.isEmpty) None else Some(ColoredChain(captureChain, player)))
      }
    }
  }
  def putPoint(pos: Pos): Field =
    putPoint(pos, lastPlayer.getOrElse(Player.Red))
  override def equals(o: Any): Boolean = o match {
    case that: Field =>
      that.vector == vector &&
        that.scoreRed == scoreRed &&
        that.scoreBlack == scoreBlack &&
        that.moves == moves &&
        that.lastSurroundChain == lastSurroundChain
    case _ => false
  }
  override def hashCode: Int = {
    val n = 7
    val h1 = vector.hashCode
    val h2 = h1 * n + scoreRed.hashCode
    val h3 = h2 * n + scoreBlack.hashCode
    val h4 = h3 * n + moves.hashCode
    val h5 = h4 * n + surroundChain.hashCode
    h5
  }
}

object Field extends ((Int, Int) => Field) {
  def apply(width: Int, height: Int): Field =
    new Field(Vector2D.fill(width, height)(EmptyPosValue), 0, 0, Nil, None)
}