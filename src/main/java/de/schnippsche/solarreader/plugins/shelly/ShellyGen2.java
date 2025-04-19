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
import de.schnippsche.solarreader.backend.field.PropertyField;
import de.schnippsche.solarreader.backend.provider.CommandProviderProperty;
import de.schnippsche.solarreader.backend.provider.ProviderProperty;
import de.schnippsche.solarreader.backend.table.Table;
import de.schnippsche.solarreader.backend.util.JsonTools;
import de.schnippsche.solarreader.backend.util.Setting;
import de.schnippsche.solarreader.backend.util.StringConverter;
import de.schnippsche.solarreader.database.ProviderData;
import de.schnippsche.solarreader.frontend.ui.ValueText;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tinylog.Logger;

/**
 * The {@link ShellyGen2} class implements the {@link ShellyIfc} interface, providing the specific
 * behavior and functionality for interacting with Shelly Gen 2 devices. This class is responsible
 * for executing the operations required to manage Shelly devices of the Gen 2 series, including
 * device initialization, command execution, and configuration handling.
 *
 * <p>The class implements methods for device setup, command analysis, and interaction with Shelly
 * Gen 2 devices over HTTP. It ensures compatibility with the Shelly system by adhering to the
 * contract defined in the {@link ShellyIfc} interface.
 */
public class ShellyGen2 implements ShellyIfc {
  private static final String BASE_URL = "http://{provider_host}/rpc/";
  private final ConnectionFactory<HttpConnection> connectionFactory;
  private Setting currentSetting;
  private ArrayList<Command> availableCommandList;
  private HttpConnection httpConnection;

  /**
   * Constructs a new instance of the {@link ShellyGen2} class with the specified {@link
   * ConnectionFactory} for creating HTTP connections. This constructor initializes the connection
   * factory to manage the communication with the Shelly Gen 2 device and prepares an empty list of
   * available commands.
   *
   * @param connectionFactory the {@link ConnectionFactory} used to create and manage HTTP
   *     connections for interacting with the Shelly Gen 2 device.
   */
  public ShellyGen2(ConnectionFactory<HttpConnection> connectionFactory) {
    this.connectionFactory = connectionFactory;
    this.availableCommandList = new ArrayList<>();
  }

  @Override
  public void doOnFirstRun(ProviderData providerData, ResourceBundle resourceBundle)
      throws IOException, InterruptedException {
    setSetting(providerData.getSetting());
    // Shelly Plus:
    // rpc/shelly.GetConfig ,
    // rpc/shelly.GetStatus,
    // rpc/shelly.GetDeviceInfo.
    // rpc/shelly.GetComponents,
    // rpc/sys.GetConfig,
    // rpc/sys.GetStatus
    // rpc/Input.GetConfig?id=0
    // rpc/Input.GetStatus?id=0
    List<ProviderProperty> providerProperties = new ArrayList<>();
    CommandProviderProperty configProperty = getShellyConfigCommandProperty(currentSetting);
    if (!configProperty.getPropertyFieldList().isEmpty()) providerProperties.add(configProperty);
    CommandProviderProperty sysConfigCommandProperty = getSysConfigCommandProperty(currentSetting);
    if (!sysConfigCommandProperty.getPropertyFieldList().isEmpty())
      providerProperties.add(sysConfigCommandProperty);
    CommandProviderProperty sysStatusCommandProperty = getSysStatusCommandProperty(currentSetting);
    if (!sysStatusCommandProperty.getPropertyFieldList().isEmpty())
      providerProperties.add(sysStatusCommandProperty);
    CommandProviderProperty statusProperty = getShellyStatusCommandProperty(currentSetting);
    if (!statusProperty.getPropertyFieldList().isEmpty()) providerProperties.add(statusProperty);

    CommandProviderProperty shellyComponentsProperty = getShellyComponentsProperty(currentSetting);
    if (!shellyComponentsProperty.getPropertyFieldList().isEmpty())
      providerProperties.add(shellyComponentsProperty);
    CommandProviderProperty deviceInfoProperty = getShellyDeviceInfoCommandProperty(currentSetting);
    if (!deviceInfoProperty.getPropertyFieldList().isEmpty())
      providerProperties.add(deviceInfoProperty);
    providerData.setProviderProperties(providerProperties);
    providerData.setProviderPropertiesChanged(true);
    availableCommandList =
        analyzeAvailableCommands(configProperty.getPropertyFieldList(), resourceBundle);
    providerData.setAvailableCommands(availableCommandList);
    Logger.debug("available commands: {}", availableCommandList.size());
  }

  @Override
  public List<Command> getAvailableCommands() {
    return availableCommandList;
  }

  @Override
  public void workCommandProperty(
      HttpConnection httpConnection,
      CommandProviderProperty property,
      Map<String, Object> variables)
      throws IOException, InterruptedException {
    String urlPattern = property.getCommand();
    Map<String, Object> values =
        new JsonTools()
            .getSimpleMapFromJsonString(
                httpConnection.getAsString(getUrl(currentSetting, urlPattern)));
    property.setCachedValue(values);
    new MapCalculator().calculate(values, property.getPropertyFieldList(), variables);
  }

  public void setSetting(Setting newSetting) {
    this.currentSetting = newSetting;
    this.httpConnection = connectionFactory.createConnection(currentSetting);
  }

  @Override
  public void sendCommand(HttpConnection httpConnection, SendCommand sendCommand)
      throws IOException, InterruptedException {

    // Cover
    //  http://192.168.33.1/rpc/Cover.Open?id=0
    //  http://192.168.33.1/rpc/Cover.Close?id=0
    // Switch
    //  http://192.168.33.1/rpc/Switch.Toggle?id=0
    //  http://192.168.33.1/rpc/Switch.Set?id=0&on=true
    // Light
    // http://192.168.33.1/rpc/Light.Toggle?id=0
    // http://192.168.33.1/rpc/Light.Set?id=0&on=true
    //
    String stringBuilder = BASE_URL + sendCommand.getSend();
    URL url = getUrl(currentSetting, stringBuilder);
    Logger.debug("send action url {}", url);
    httpConnection.get(url);
  }

  @Override
  public List<Table> analyzeTableFields(List<PropertyField> providerFields) {
    return List.of();
  }

  @Override
  public ArrayList<Command> analyzeAvailableCommands(
      List<PropertyField> providerFields, ResourceBundle resourceBundle) {
    // Define commonly used ValueText options

    // Define text labels
    Pattern pattern = Pattern.compile("configuration_(cover|switch|light)_(\\d+)_id");
    Set<Command> commandSet = new HashSet<>();
    for (PropertyField field : providerFields) {
      Matcher matcher = pattern.matcher(field.getFieldName());
      if (matcher.find()) {
        String type = matcher.group(1);
        String label;
        int index = Integer.parseInt(matcher.group(2));
        switch (type) {
          case "switch":
            label = "shelly.relay";
            break;
          case "light":
            label = "shelly.light";
            break;
          case "cover":
            label = "shelly.roller";
            break;
          default:
            label = type;
        }
        String id = "?id=" + index;
        List<ValueText> optionList = new ArrayList<>();
        switch (type) {
          case "switch":
            optionList.add(new ValueText("Switch.Set" + id + "&on=true", "shelly.option.on"));
            optionList.add(new ValueText("Switch.Set" + id + "&on=false", "shelly.option.off"));
            optionList.add(new ValueText("Switch.Toggle" + id, "shelly.option.toggle"));

            break;
          case "light":
            optionList.add(new ValueText("Light.Set" + id + "&on=true", "shelly.option.on"));
            optionList.add(new ValueText("Light.Set" + id + "&on=false", "shelly.option.off"));
            optionList.add(new ValueText("Light.Toggle" + id, "shelly.option.toggle"));
            break;
          case "cover":
            optionList.add(new ValueText("Cover.Open" + id, "shelly.option.open"));
            optionList.add(new ValueText("Cover.Close" + id, "shelly.option.close"));
            for (int pos = 0; pos <= 100; pos += 10) {
              optionList.add(
                  new ValueText(
                      "Cover.GoToPosition" + id + "&pos=" + pos, "shelly.roller.position." + pos));
            }
            break;
          default:
            continue;
        }
        commandSet.add(new Command("Shelly", label, optionList, null, index + 1));
      }
    }
    ArrayList<Command> sortedCommandList = new ArrayList<>(commandSet);
    sortedCommandList.sort(null);
    return sortedCommandList;
  }

  private URL getUrl(Setting setting, String urlPattern) throws IOException {
    String urlString =
        new StringConverter(urlPattern).replaceNamedPlaceholders(setting.getConfigurationValues());
    Logger.debug("url:{}", urlString);
    return new StringConverter(urlString).toUrl();
  }

  private CommandProviderProperty getShellyConfigCommandProperty(Setting setting)
      throws IOException, InterruptedException {
    CommandProviderProperty configuration =
        createCommandProperty(setting, "Configuration", "shelly.GetConfig");
    configuration.setCacheDurationInSeconds(1810);
    return configuration;
  }

  private CommandProviderProperty getSysConfigCommandProperty(Setting setting)
      throws IOException, InterruptedException {
    CommandProviderProperty sysConfiguration =
        createCommandProperty(setting, "SysConfiguration", "sys.GetConfig");
    sysConfiguration.setCacheDurationInSeconds(3600);
    return sysConfiguration;
  }

  private CommandProviderProperty getShellyComponentsProperty(Setting setting)
      throws IOException, InterruptedException {
    return createCommandProperty(setting, "Components", "shelly.GetComponents");
  }

  private CommandProviderProperty getSysStatusCommandProperty(Setting setting)
      throws IOException, InterruptedException {
    CommandProviderProperty sysStatus =
        createCommandProperty(setting, "SysStatus", "sys.GetStatus");
    sysStatus.setCacheDurationInSeconds(3600);
    return sysStatus;
  }

  private CommandProviderProperty getShellyDeviceInfoCommandProperty(Setting setting)
      throws IOException, InterruptedException {
    CommandProviderProperty deviceInfo =
        createCommandProperty(setting, "DeviceInfo", "shelly.GetDeviceInfo");
    deviceInfo.setCacheDurationInSeconds(3600);
    return deviceInfo;
  }

  private CommandProviderProperty getShellyStatusCommandProperty(Setting setting)
      throws IOException, InterruptedException {
    return createCommandProperty(setting, "Status", "shelly.GetStatus");
  }

  private CommandProviderProperty createCommandProperty(
      Setting setting, String name, String groupName) throws IOException, InterruptedException {
    CommandProviderProperty commandProviderProperty = new CommandProviderProperty();
    commandProviderProperty.setName(name);
    String urlPattern = BASE_URL + groupName;
    commandProviderProperty.setCommand(urlPattern);
    URL url = getUrl(setting, urlPattern);
    List<PropertyField> allSupportedFields =
        new ArrayList<>(getAvailableFields(url, name.toLowerCase()));
    commandProviderProperty.getPropertyFieldList().addAll(allSupportedFields);
    return commandProviderProperty;
  }

  private Set<PropertyField> getAvailableFields(URL url, String groupName)
      throws IOException, InterruptedException {
    String json = httpConnection.getAsString(url);
    return new JsonTools().getAvailableFieldsFromJson(json, groupName);
  }
}
