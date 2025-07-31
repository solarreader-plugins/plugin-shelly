/*
 * Copyright (c) 2024-2025 Stefan Toengi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.schnippsche.solarreader.plugins.shelly;

import de.schnippsche.solarreader.backend.calculator.MapCalculator;
import de.schnippsche.solarreader.backend.command.Command;
import de.schnippsche.solarreader.backend.command.SendCommand;
import de.schnippsche.solarreader.backend.connection.general.ConnectionFactory;
import de.schnippsche.solarreader.backend.connection.network.HttpConnection;
import de.schnippsche.solarreader.backend.connection.network.HttpConnectionFactory;
import de.schnippsche.solarreader.backend.provider.AbstractHttpProvider;
import de.schnippsche.solarreader.backend.provider.CommandProviderProperty;
import de.schnippsche.solarreader.backend.provider.ProviderProperty;
import de.schnippsche.solarreader.backend.table.Table;
import de.schnippsche.solarreader.backend.util.Setting;
import de.schnippsche.solarreader.database.Activity;
import de.schnippsche.solarreader.frontend.ui.HtmlInputType;
import de.schnippsche.solarreader.frontend.ui.HtmlWidth;
import de.schnippsche.solarreader.frontend.ui.UIInputElementBuilder;
import de.schnippsche.solarreader.frontend.ui.UIList;
import de.schnippsche.solarreader.frontend.ui.UITextElementBuilder;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import org.tinylog.Logger;

/**
 * The {@link Shelly} class is a specific implementation of the {@link AbstractHttpProvider} for
 * interacting with Shelly devices via HTTP. It extends {@link AbstractHttpProvider} and is used to
 * manage communication with Shelly smart devices, such as smart plugs, lights, or switches, using
 * the HTTP protocol.
 *
 * <p>This class provides methods for sending HTTP requests to Shelly devices, retrieving data, and
 * performing actions such as controlling device states. It abstracts the complexities of HTTP
 * communication with Shelly devices, enabling easier integration and control of Shelly devices
 * within a broader system.
 */
public class Shelly extends AbstractHttpProvider {

  private static final String UNKNOWN = "unknown";
  private static final String SHELLY_PLUS_1_MINI = "Shelly Plus 1 Mini";
  private static final String SHELLY_PRO_1 = "Shelly Pro 1";
  private static final String SHELLY_PRO_2 = "Shelly Pro 2";
  // see https://github.com/markus7017/myfiles/blob/master/shelly/README.md
  private static final Map<String, String> identifierMap =
      Map.<String, String>ofEntries(
          // Generation 1
          Map.entry("SHSW-1", "Shelly 1 Single Relay Switch"),
          Map.entry("SHSW-L", "Shelly 1L Single Relay Switch"),
          Map.entry("SHSW-PM", "Shelly Single Relay Switch with integrated Power Meter"),
          Map.entry("SHSW-21", "Shelly 2"),
          Map.entry("SHSW-25", "Shelly 2.5"),
          Map.entry("SHSW-44", "Shelly 4x Relay Switch"),
          Map.entry("SHDM-1", "Shelly Dimmer"),
          Map.entry("SHDM-2", "Shelly Dimmer2"),
          Map.entry("SHIX3-1", "Shelly ix3"),
          Map.entry("SHUNI-1", "Shelly UNI"),
          Map.entry("SHPLG2-1", "Shelly Plug"),
          Map.entry("SHPLG-S", "Shelly Plug-S"),
          Map.entry("SHEM", "Shelly EM with integrated Power Meters"),
          Map.entry("SHEM-3", "Shelly 3EM with 3 integrated Power Meter"),
          Map.entry("SHRGBW2", "Shelly RGBW2 Controller"),
          Map.entry("SHBLB-1", "Shelly Bulb"),
          Map.entry("SHBDUO-1", "Shelly Duo"),
          Map.entry("SHCB-1", "Shelly Duo Color G10"),
          Map.entry("SHVIN-1", "Shelly Vintage (White Mode)"),
          Map.entry("SHHT-1", "Shelly Sensor (temperature+humidity)"),
          Map.entry("SHWT-1", "Shelly Flood Sensor"),
          Map.entry("SHSM-1", "Shelly Smoke Sensor"),
          Map.entry("SHMOS-01", "Shelly Motion Sensor"),
          Map.entry("SHMOS-02", "Shelly Motion Sensor 2"),
          Map.entry("SHGS-1", "Shelly Gas Sensor"),
          Map.entry("SHDW-1", "Shelly Door/Window"),
          Map.entry("SHDW-2", "Shelly Door/Window 2"),
          Map.entry("SHBTN-1", "Shelly Button 1"),
          Map.entry("SHBTN-2", "Shelly Button 2"),
          Map.entry("SHSEN-1", "Shelly Motion and IR Controller"),
          Map.entry("SHTRV-01", "Shelly TRV"),

          // Generation 2 Plus series
          Map.entry("SNSW-001X16EU", "Shelly Plus 1"),
          Map.entry("SNSW-001P16EU", "Shelly Plus 1PM"),
          Map.entry("SNSW-002P16EU", "Shelly Plus 2PM"),
          Map.entry("SNSW-102P16EU", "Shelly Plus 2PM"),
          Map.entry("SNPL-00112EU", "Shelly Plus Plug-S"),
          Map.entry("SNPL-00110IT", "Shelly Plus Plug-IT"),
          Map.entry("SNPL-00110UK", "Shelly Plus Plug-UK"),
          Map.entry("SNPL-00110US", "Shelly Plus Plug-US"),
          Map.entry("SNSN-0024X", "Shelly Plus i4 AC"),
          Map.entry("SNSN-0D24X", "Shelly Plus i4 DC"),
          Map.entry("SNSN-0013A", "Shelly Plus HT"),
          Map.entry("S3SN-0U12A", "Shelly Plus HT Gen3"),
          Map.entry("SNSN-0031Z", "Shelly Plus Smoke sensor"),
          Map.entry("SNDM-0013US", "Shelly Plus Wall Dimmer US"),
          Map.entry("SNDC-0D4P10WW", "Shelly Plus RGBW PM"),
          Map.entry("SAWD-0A1XX10EU1", "Shelly Plus Wall Display"),
          Map.entry("SNGW-BT01", "SHelly BLU Gateway"),
          // Generation 2 Plus Mini series (incl. Gen 3)
          Map.entry("SNSW-001X8EU", SHELLY_PLUS_1_MINI),
          Map.entry("SNSW-001P8EU", SHELLY_PLUS_1_MINI),
          Map.entry("S3SW-001P8EU", SHELLY_PLUS_1_MINI),
          Map.entry("SNPM-001PCEU16", SHELLY_PLUS_1_MINI),
          Map.entry("S3PM-001PCEU16", SHELLY_PLUS_1_MINI),
          // Generation 2 Pro series
          Map.entry("SPSW-001XE16EU", SHELLY_PRO_1),
          Map.entry("SPSW-101XE16EU", SHELLY_PRO_1),
          Map.entry("SPSW-201XE16EU", SHELLY_PRO_1),
          Map.entry("SPSW-001PE16EU", SHELLY_PRO_1),
          Map.entry("SPSW-101PE16EU", SHELLY_PRO_1),
          Map.entry("SPSW-201PE16EU", SHELLY_PRO_1),
          Map.entry("SPSW-002XE16EU", SHELLY_PRO_2),
          Map.entry("SPSW-102XE16EU", SHELLY_PRO_2),
          Map.entry("SPSW-202XE16EU", SHELLY_PRO_2),
          Map.entry("SPSW-002PE16EU", SHELLY_PRO_2),
          Map.entry("SPSW-102PE16EU", SHELLY_PRO_2),
          Map.entry("SPSW-202PE16EU", SHELLY_PRO_2),
          Map.entry("SPSW-003XE16EU", "Shelly Pro 3"),
          Map.entry("SPEM-003CEBEU", "Shelly Pro 3"),
          Map.entry("SPEM-002CEBEU50", "Shelly Pro EM50"),
          Map.entry("SPSW-004PE16EU", "Shelly Pro 4 PM"),
          Map.entry("SPSW-104PE16EU", "Shelly Pro 4 PM"),
          // Shelly BLU
          Map.entry("SBBT", "Shelly BLU Button 1"),
          Map.entry("SBDW", "Shelly BLU Door/Window"),
          Map.entry("SBMO", "Shelly BLU Motion"),
          Map.entry("SBHT", "Shelly BLU H&T"));

  private ShellyIfc shellyImpl;

  /**
   * Constructs a new instance of the {@link Shelly} class using the default {@link
   * HttpConnectionFactory}. This constructor initializes the Shelly provider with the default HTTP
   * connection factory, allowing communication with Shelly devices over HTTP. The instance is
   * configured to interact with Shelly devices using the default connection settings.
   */
  public Shelly() {
    this(new HttpConnectionFactory());
  }

  /**
   * Constructs a new instance of the {@link Shelly} class with a custom {@link ConnectionFactory}
   * for creating and managing HTTP connections.
   *
   * <p>This constructor provides the flexibility to use a custom connection factory for HTTP
   * communication with Shelly devices. The custom factory allows for specific configuration of the
   * HTTP connection, such as setting timeouts, headers, or other parameters. The constructor also
   * initializes a {@link ShellyGen1} instance, which implements Shelly-specific device
   * functionality, and configures the Shelly provider to interact with Shelly devices accordingly.
   *
   * @param connectionFactory the {@link ConnectionFactory} used to create and manage HTTP
   *     connections for communication with Shelly devices.
   */
  public Shelly(ConnectionFactory<HttpConnection> connectionFactory) {
    super(connectionFactory);
    shellyImpl = new ShellyGen1(connectionFactory);
    Logger.debug("instantiate {}", this.getClass().getName());
  }

  /**
   * Retrieves the resource bundle for the plugin based on the specified locale.
   *
   * <p>This method overrides the default implementation to return a {@link ResourceBundle} for the
   * plugin using the provided locale.
   *
   * @return The {@link ResourceBundle} for the plugin, localized according to the specified locale.
   */
  @Override
  public ResourceBundle getPluginResourceBundle() {
    return ResourceBundle.getBundle("shelly", locale);
  }

  @Override
  public Activity getDefaultActivity() {
    return new Activity(LocalTime.of(0, 0, 0), LocalTime.of(23, 59, 59), 1, TimeUnit.MINUTES);
  }

  @Override
  public Optional<UIList> getProviderDialog() {
    UIList uiList = new UIList();
    uiList.addElement(
        new UITextElementBuilder().withLabel(resourceBundle.getString("shelly.title")).build());
    uiList.addElement(
        new UIInputElementBuilder()
            .withName(Setting.PROVIDER_HOST)
            .withType(HtmlInputType.TEXT)
            .withColumnWidth(HtmlWidth.FULL)
            .withRequired(true)
            .withTooltip(resourceBundle.getString("shelly.host.tooltip"))
            .withLabel(resourceBundle.getString("shelly.host.text"))
            .withPlaceholder(resourceBundle.getString("shelly.host.text"))
            .withInvalidFeedback(resourceBundle.getString("shelly.required.error"))
            .build());
    uiList.addElement(
        new UIInputElementBuilder()
            .withName(Setting.OPTIONAL_USER)
            .withType(HtmlInputType.TEXT)
            .withColumnWidth(HtmlWidth.HALF)
            .withRequired(false)
            .withTooltip(resourceBundle.getString("shelly.user.tooltip"))
            .withLabel(resourceBundle.getString("shelly.user.text"))
            .withPlaceholder(resourceBundle.getString("shelly.user.text"))
            .build());
    uiList.addElement(
        new UIInputElementBuilder()
            .withName(Setting.OPTIONAL_PASSWORD)
            .withType(HtmlInputType.TEXT)
            .withColumnWidth(HtmlWidth.HALF)
            .withRequired(false)
            .withTooltip(resourceBundle.getString("shelly.password.tooltip"))
            .withLabel(resourceBundle.getString("shelly.password.text"))
            .withPlaceholder(resourceBundle.getString("shelly.password.text"))
            .build());
    return Optional.of(uiList);
  }

  @Override
  public Optional<List<ProviderProperty>> getSupportedProperties() {
    return providerData != null && providerData.getProviderProperties() != null
        ? Optional.of(providerData.getProviderProperties())
        : Optional.empty();
  }

  @Override
  public Optional<List<Table>> getDefaultTables() {
    return Optional.empty();
  }

  @Override
  public Setting getDefaultProviderSetting() {
    Setting setting = new Setting();
    setting.setProviderHost("localhost");
    setting.setProviderPort(80);
    return setting;
  }

  @Override
  public void configurationHasChanged() {
    super.configurationHasChanged();
    shellyImpl.setSetting(providerData.getSetting());
  }

  @Override
  public String testProviderConnection(Setting testSetting)
      throws IOException, InterruptedException {
    ShellyGen1 test = new ShellyGen1(connectionFactory);
    test.setSetting(testSetting);
    Map<String, Object> result = test.getStandardValues();
    String name;
    String type;
    if (isPlusDevice(result)) {
      name = String.valueOf(result.getOrDefault("name", ""));
      type = String.valueOf(result.getOrDefault("model", UNKNOWN));
    } else {
      name = test.getName();
      type = String.valueOf(result.getOrDefault("type", UNKNOWN));
    }
    // Beide verstehen /shelly, bei Gen 1 ist "type" relevant und bei shelly plus ist "model"
    // relevant
    if (!name.isEmpty()) {
      name = "'" + name + "'";
    }
    type = identifierMap.getOrDefault(type, UNKNOWN);
    String message = resourceBundle.getString("shelly.connection.successful");
    String returnCode = MessageFormat.format(message, name, type);
    Logger.debug("return code is {}", returnCode);
    return returnCode;
  }

  @Override
  public void doOnFirstRun() throws IOException, InterruptedException {
    // which version ?
    ShellyGen1 shellyGen1 = new ShellyGen1(connectionFactory);
    shellyGen1.setSetting(providerData.getSetting());
    Map<String, Object> values = shellyGen1.getStandardValues();
    if (isPlusDevice(values)) {
      shellyImpl = new ShellyGen2(connectionFactory);
      shellyImpl.setSetting(providerData.getSetting());
    } else {
      shellyImpl = shellyGen1;
    }
    if (providerData.getProviderProperties() == null) {
      shellyImpl.doOnFirstRun(providerData, resourceBundle);
    }

    if (providerData.getTableList() == null) {
      Optional<ProviderProperty> statusActionProperty =
          providerData.getProviderProperties().stream()
              .filter(p -> p.getName().equals("Status"))
              .findFirst();
      if (statusActionProperty.isPresent()) {
        providerData.setTableList(
            shellyImpl.analyzeTableFields(statusActionProperty.get().getPropertyFieldList()));
        providerData.setTablesChanged(true);
      }
    }
  }

  @Override
  public boolean doActivityWork(Map<String, Object> variables)
      throws IOException, InterruptedException {
    HttpConnection httpConnection = getConnection();
    workProperties(httpConnection, variables);
    return true;
  }

  @Override
  public void sendCommand(SendCommand command) throws IOException, InterruptedException {
    shellyImpl.sendCommand(getConnection(), command);
  }

  @Override
  public List<Command> getAvailableCommands() {
    return shellyImpl.getAvailableCommands();
  }

  private boolean isPlusDevice(Map<String, Object> standardValues) {
    return (standardValues.containsKey("model"));
  }

  @Override
  protected void handleCommandProperty(
      HttpConnection httpConnection,
      CommandProviderProperty commandProviderProperty,
      Map<String, Object> variables)
      throws IOException, InterruptedException {
    Logger.debug("handleCommandProperty");
    shellyImpl.workCommandProperty(httpConnection, commandProviderProperty, variables);
  }

  @Override
  protected void handleCachedCommandProperty(
      HttpConnection httpConnection,
      CommandProviderProperty commandProviderProperty,
      Map<String, Object> variables) {
    Optional.ofNullable(commandProviderProperty.getCachedValue())
        .filter(Map.class::isInstance)
        .map(
            value -> {
              @SuppressWarnings("unchecked")
              Map<String, Object> safeCast = (Map<String, Object>) value;
              return safeCast;
            })
        .ifPresent(
            cachedValue -> {
              Logger.debug("use cached value for command {}", commandProviderProperty.getCommand());
              new MapCalculator()
                  .calculate(
                      cachedValue, commandProviderProperty.getPropertyFieldList(), variables);
            });
  }
}
