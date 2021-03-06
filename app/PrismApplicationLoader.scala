import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.{Regions, Region}
import conf.{AWS, FileConfiguration, DynamoConfiguration, Identity}
import play.api.ApplicationLoader.Context
import play.api.{Mode, Configuration}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}
import utils.Logging

class PrismApplicationLoader extends GuiceApplicationLoader() with Logging {

  override protected def builder(context: Context): GuiceApplicationBuilder = {
    val identity = {
      context.environment.mode match {
        case Mode.Prod => AWS.instance.identity
        case _ => None
      }
    }.getOrElse(Identity("deploy", "prism", "DEV"))

    log.info(s"Getting config for $identity")

    val extraConfigs = List(
      DynamoConfiguration(
        new DefaultAWSCredentialsProviderChain(),
        Regions.EU_WEST_1,
        identity
      ),
      FileConfiguration(identity)
    )

    val extraConfig = extraConfigs.foldLeft(Configuration.empty)(_ ++ _.configuration(context.environment.mode))

    val combinedConfig = context.initialConfiguration ++ extraConfig

    log.info(s"Loaded config $extraConfig")

    new GuiceApplicationBuilder()
      .in(context.environment)
      .loadConfig(combinedConfig)
      .overrides(overrides(context): _*)
  }
}
