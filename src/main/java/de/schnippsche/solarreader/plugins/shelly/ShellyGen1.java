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
import de.schnippsche.solarreader.backend.table.TableCell;
import de.schnippsche.solarreader.backend.table.TableColumn;
import de.schnippsche.solarreader.backend.table.TableColumnType;
import de.schnippsche.solarreader.backend.util.JsonTools;
import de.schnippsche.solarreader.backend.util.Setting;
import de.schnippsche.solarreader.backend.util.StringConverter;
import de.schnippsche.solarreader.database.ProviderData;
import de.schnippsche.solarreader.frontend.ui.ValueText;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tinylog.Logger;

/**
 * The {@link ShellyGen1} class implements the {@link ShellyIfc} interface, providing the specific
 * behavior and functionality for interacting with Shelly Gen 1 devices. This class is responsible
 * for executing the operations required to manage Shelly devices of the Gen 1 series, such as
 * device initialization, command execution, and configuration handling.
 *
 * <p>The class implements methods for device setup, command analysis, and interaction with Shelly
 * Gen 1 devices over HTTP. It also ensures compatibility with the overall Shelly system by
 * following the contract defined in the {@link ShellyIfc} interface.
 */
public class ShellyGen1 implements ShellyIfc {
  private static final String BASE_URL = "http://{provider_host}/";
  private static final String ON = "on";
  private static final String OFF = "off";
  private static final String TOGGLE = "toggle";
  private static final String OPEN = "open";
  private static final String CLOSE = "close";
  private static final Map<String, String> tableFieldMap =
      Map.ofEntries(
          Map.entry("power", "Wirkleistung"),
          Map.entry("current", "Strom"),
          Map.entry("voltage", "Spannung"),
          Map.entry("total", "Leistung_VerbrauchGesamt"),
          Map.entry("total_returned", "Leistung_EinspeisungGesamt"),
          Map.entry("pf", "PowerFactor"));
  private static final Map<String, String> indexMap =
      Map.ofEntries(Map.entry("0", "_R"), Map.entry("1", "_S"), Map.entry("2", "_T"));
  private final ConnectionFactory<HttpConnection> connectionFactory;
  private ArrayList<Command> availableCommandList;
  private Setting currentSetting;
  private HttpConnection httpConnection;

  /**
   * Constructs a new instance of the {@link ShellyGen1} class with the specified {@link
   * HttpConnection} factory. This constructor initializes the connection factory for managing HTTP
   * connections and prepares an empty list of available commands for the Shelly Gen 1 device.
   *
   * @param connectionFactory the {@link ConnectionFactory} used to create HTTP connections for
   *     interacting with the Shelly Gen 1 device.
   */
  public ShellyGen1(ConnectionFactory<HttpConnection> connectionFactory) {
    this.connectionFactory = connectionFactory;
    this.availableCommandList = new ArrayList<>();
  }

  @Override
  public void setSetting(Setting newSetting) {
    this.currentSetting = newSetting;
    this.httpConnection = connectionFactory.createConnection(currentSetting);
  }

  @Override
  public void doOnFirstRun(ProviderData providerData, ResourceBundle resourceBundle)
      throws IOException, InterruptedException {
    setSetting(providerData.getSetting());
    List<ProviderProperty> providerProperties = new ArrayList<>();
    CommandProviderProperty settingsProperty = getSettingsCommandProperty(currentSetting);
    providerProperties.add(settingsProperty);
    CommandProviderProperty statusProperty = getStatusCommandProperty(currentSetting);
    providerProperties.add(statusProperty);
    CommandProviderProperty shellyProperty = getShellyCommandProperty(currentSetting);
    providerProperties.add(shellyProperty);
    providerData.setProviderProperties(providerProperties);
    providerData.setProviderPropertiesChanged(true);
    availableCommandList =
        analyzeAvailableCommands(settingsProperty.getPropertyFieldList(), resourceBundle);
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

  @Override
  public void sendCommand(HttpConnection httpConnection, SendCommand sendCommand)
      throws IOException, InterruptedException {
    String selectedOption = sendCommand.getSend();
    //   actionUrl = http://192.168.178.224/relay/0?turn=on&timer=5
    String urlPattern = String.format("%s%s", BASE_URL, selectedOption);
    URL url = getUrl(currentSetting, urlPattern);
    Logger.debug("send action url {}", url);
    httpConnection.get(url);
  }

  public List<Table> analyzeTableFields(List<PropertyField> providerFields) {
    List<Table> tables = new ArrayList<>();
    boolean valid = false;
    Table acTable = new Table("AC");
    tables.add(acTable);
    for (PropertyField providerField : providerFields) {
      TableCell cell = new TableCell(providerField.getFieldName());
      if ("status_temperature".equals(providerField.getFieldName())) {
        Table serviceTable = new Table("Service");
        serviceTable.addColumnAndCellAtFirstRow(
            new TableColumn("Temperatur", TableColumnType.NUMBER), cell);
        tables.add(serviceTable);
        valid = true;
      } else {
        String[] pieces = providerField.getFieldName().split("_");
        // meters and emeters !
        if (pieces.length == 4 && ("status".equals(pieces[0]) && (pieces[1].endsWith("meters")))) {
          String index = indexMap.getOrDefault(pieces[2], "");
          String name = tableFieldMap.get(pieces[3]);
          if (name != null) {
            acTable.addColumnAndCellAtFirstRow(
                new TableColumn(name + index, TableColumnType.NUMBER), cell);
            valid = true;
          }
        }
      }
    }
    return (valid ? tables : Collections.emptyList());
  }

  @Override
  public ArrayList<Command> analyzeAvailableCommands(
      List<PropertyField> providerFields, ResourceBundle resourceBundle) {

    Pattern pattern = Pattern.compile("(relays|lights|rollers)_(\\d+)_(ison|power)");
    Set<Command> commandSet = new HashSet<>();
    for (PropertyField field : providerFields) {
      Matcher matcher = pattern.matcher(field.getFieldName());
      if (matcher.find()) {
        String type = matcher.group(1);
        int index = Integer.parseInt(matcher.group(2));
        String label;
        List<ValueText> optionList = new ArrayList<>();
        String internal;
        switch (type) {
          case "relays":
            internal = "relay/" + index + "?turn=";
            label = "shelly.relay";
            optionList.add(new ValueText(internal + ON, "shelly.option.on"));
            optionList.add(new ValueText(internal + OFF, "shelly.option.off"));
            optionList.add(new ValueText(internal + TOGGLE, "shelly.option.toggle"));
            break;
          case "lights":
            internal = "light/" + index + "?turn=";
            label = "shelly.light";
            optionList.add(new ValueText(internal + ON, "shelly.option.on"));
            optionList.add(new ValueText(internal + OFF, "shelly.option.off"));
            optionList.add(new ValueText(internal + TOGGLE, "shelly.option.toggle"));
            break;
          case "rollers":
            internal = "roller/" + index + "/go=";
            label = "shelly.roller";
            optionList.add(new ValueText(internal + OPEN, "shelly.option.open"));
            optionList.add(new ValueText(internal + CLOSE, "shelly.option.close"));
            internal += "to_pos&roller_pos=";
            for (int pos = 0; pos <= 100; pos += 10) {
              optionList.add(new ValueText(internal + pos, "shelly.roller.position." + pos));
            }
            break;
          default:
            continue;
        }
        commandSet.add(new Command("Shelly", label, optionList, null, index + 1));
      }
    }

    ArrayList<Command> sortedCommandList = new ArrayList<>(commandSet);
    sortedCommandList.sort(null); // Using sort with natural ordering
    return sortedCommandList;
  }

  /**
   * Retrieves the name associated with the current setting by making an HTTP request to the
   * specified URL and parsing the response.
   *
   * <p>This method sends an HTTP GET request to the URL formed by appending "settings" to the
   * BASE_URL, retrieves the response as a JSON string, and then extracts the "name" value from the
   * resulting JSON map. If the "name" key is not found, an empty string is returned.
   *
   * @return the name retrieved from the server, or an empty string if the "name" key is not found.
   * @throws IOException if an I/O error occurs during the HTTP request.
   * @throws InterruptedException if the thread is interrupted while waiting for the HTTP request to
   *     complete.
   * @see #getUrl(Setting, String)
   */
  public String getName() throws IOException, InterruptedException {
    URL testUrl = getUrl(currentSetting, BASE_URL + "settings");
    HttpConnection testConnection = connectionFactory.createConnection(currentSetting);
    String json = testConnection.getAsString(testUrl);
    Map<String, Object> result = new JsonTools().getSimpleMapFromJsonString(json);
    return String.valueOf(result.getOrDefault("name", ""));
  }

  /**
   * Retrieves standard values from the server by making an HTTP request to the "shelly" endpoint
   * and parsing the response.
   *
   * <p>This method sends an HTTP GET request to the URL formed by appending "shelly" to the
   * BASE_URL, retrieves the response as a JSON string, and returns the result as a map of key-value
   * pairs.
   *
   * @return a {@link Map} containing the standard values retrieved from the server.
   * @throws IOException if an I/O error occurs during the HTTP request.
   * @throws InterruptedException if the thread is interrupted while waiting for the HTTP request to
   *     complete.
   * @see #getUrl(Setting, String)
   */
  public Map<String, Object> getStandardValues() throws IOException, InterruptedException {
    URL testUrl = getUrl(currentSetting, BASE_URL + "shelly");
    Logger.debug("test url {}", testUrl);
    HttpConnection testConnection = connectionFactory.createConnection(currentSetting);
    testConnection.test(testUrl, HttpConnection.CONTENT_TYPE_JSON);
    String json = testConnection.getAsString(testUrl);
    Logger.debug("result is {}", json);
    return new JsonTools().getSimpleMapFromJsonString(json);
  }

  private URL getUrl(Setting setting, String urlPattern) throws IOException {
    String urlString =
        new StringConverter(urlPattern).replaceNamedPlaceholders(setting.getConfigurationValues());
    Logger.debug("url:{}", urlString);
    return new StringConverter(urlString).toUrl();
  }

  private CommandProviderProperty getSettingsCommandProperty(Setting setting)
      throws IOException, InterruptedException {
    CommandProviderProperty commandProperty =
        createCommandProperty(setting, "Settings", "settings");
    commandProperty.setCacheDurationInSeconds(3600);
    return commandProperty;
  }

  private CommandProviderProperty getShellyCommandProperty(Setting setting)
      throws IOException, InterruptedException {

    CommandProviderProperty commandProperty = createCommandProperty(setting, "Shelly", "shelly");
    commandProperty.setCacheDurationInSeconds(3600);
    return commandProperty;
  }

  private CommandProviderProperty getStatusCommandProperty(Setting setting)
      throws IOException, InterruptedException {
    return createCommandProperty(setting, "Status", "status");
  }

  private CommandProviderProperty createCommandProperty(
      Setting setting, String name, String groupName) throws IOException, InterruptedException {
    CommandProviderProperty commandProviderProperty = new CommandProviderProperty();
    commandProviderProperty.setName(name);
    String urlPattern = BASE_URL + groupName;
    commandProviderProperty.setCommand(urlPattern);
    URL url = getUrl(setting, urlPattern);
    List<PropertyField> allSupportedFields = new ArrayList<>(getAvailableFields(url, groupName));
    commandProviderProperty.getPropertyFieldList().addAll(allSupportedFields);
    return commandProviderProperty;
  }

  private Set<PropertyField> getAvailableFields(URL url, String groupName)
      throws IOException, InterruptedException {
    String json = httpConnection.getAsString(url);
    return new JsonTools().getAvailableFieldsFromJson(json, groupName);
  }
}
