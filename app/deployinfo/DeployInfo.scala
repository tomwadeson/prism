package deployinfo

import org.joda.time.DateTime

object DeployInfo {
  def apply(): DeployInfo = DeployInfo(DeployInfoJsonInputFile(Nil,None,Map.empty), None)

  def transpose[A](xs: List[List[A]]): List[List[A]] = xs.filter(_.nonEmpty) match {
    case Nil => Nil
    case ys: List[List[A]] => ys.map{ _.head }::transpose(ys.map{ _.tail })
  }

  def transposeHostsByGroup(hosts: List[Host]): List[Host] = {
    val listOfGroups = hosts.groupBy(_.tags.get("group").getOrElse("")).toList.sortBy(_._1).map(_._2)
    transpose(listOfGroups).fold(Nil)(_ ::: _)
  }
}

case class DeployInfo(input:DeployInfoJsonInputFile, createdAt:Option[DateTime]) {

  def asHost(host: DeployInfoHost) = {
    val tags:List[(String,String)] =
      List("group" -> host.group) ++
        host.created_at.map("created_at" -> _) ++
        host.dnsname.map("dnsname" -> _) ++
        host.instancename.map("instancename" -> _) ++
        host.internalname.map("internalname" -> _)
    Host(host.arn, host.hostname, Set(App(host.app)), host.stage, tags = tags.toMap)
  }

  def filterHosts(p: Host => Boolean) = this.copy(input = input.copy(hosts = input.hosts.filter(jsonHost => p(asHost(jsonHost)))))

  val hosts = input.hosts.map(asHost)
  val data = input.data mapValues { dataList =>
    dataList.map { data => Data(data.app, data.stage, data.value, data.comment) }
  }

  lazy val knownHostStages: List[String] = hosts.map(_.stage).distinct.sorted
  lazy val knownHostApps: List[Set[App]] = hosts.map(_.apps).distinct.sortWith(_.toList.head.name < _.toList.head.name)

  def knownHostApps(stage: String): List[Set[App]] = knownHostApps.filter(stageAppToHostMap.contains(stage, _))

  lazy val knownKeys: List[String] = data.keys.toList.sorted

  def dataForKey(key: String): List[Data] = data.get(key).getOrElse(List.empty)
  def knownDataStages(key: String) = data.get(key).toList.flatMap {_.map(_.stage).distinct.sortWith(_.toString < _.toString)}
  def knownDataApps(key: String): List[String] = data.get(key).toList.flatMap{_.map(_.app).distinct.sortWith(_.toString < _.toString)}

  lazy val stageAppToHostMap: Map[(String,Set[App]),List[Host]] = hosts.groupBy(host => (host.stage,host.apps)).mapValues(DeployInfo.transposeHostsByGroup)
  def stageAppToDataMap(key: String): Map[(String,String),List[Data]] = data.get(key).map {_.groupBy(key => (key.stage,key.app))}.getOrElse(Map.empty)

  def firstMatchingData(key: String, app:App, stage:String): Option[Data] = {
    val matchingList = data.getOrElse(key, List.empty)
    matchingList.find(data => data.appRegex.findFirstMatchIn(app.name).isDefined && data.stageRegex.findFirstMatchIn(stage).isDefined)
  }
}

case class Host(
    id: String,
    name: String,
    apps: Set[App] = Set.empty,
    stage: String = "NO_STAGE",
    connectAs: Option[String] = None,
    tags: Map[String, String] = Map.empty)
{
  def app(name: String) = this.copy(apps = apps + App(name))
  def app(app: App) = this.copy(apps= apps + app)

  def as(user: String) = this.copy(connectAs = Some(user))

  // this allows "resin" @: Host("some-host")
  def @:(user: String) = as(user)

  lazy val connectStr = (connectAs map { _ + "@" } getOrElse "") + name
}

case class Data(
  app: String,
  stage: String,
  value: String,
  comment: Option[String]
) {
  lazy val appRegex = ("^%s$" format app).r
  lazy val stageRegex = ("^%s$" format stage).r
}

case class App(name: String)