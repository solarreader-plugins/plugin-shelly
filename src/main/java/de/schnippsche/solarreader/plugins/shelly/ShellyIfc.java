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

import de.schnippsche.solarreader.backend.command.Command;
import de.schnippsche.solarreader.backend.command.SendCommand;
import de.schnippsche.solarreader.backend.connection.network.HttpConnection;
import de.schnippsche.solarreader.backend.field.PropertyField;
import de.schnippsche.solarreader.backend.provider.CommandProviderProperty;
import de.schnippsche.solarreader.backend.table.Table;
import de.schnippsche.solarreader.backend.util.Setting;
import de.schnippsche.solarreader.database.ProviderData;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * The {@link ShellyIfc} interface defines the contract for interacting with Shelly devices or
 * services. It provides methods to handle device setup, execute commands, analyze available
 * commands, and manage settings. Implementing this interface allows classes to perform operations
 * such as initializing the device on its first run, sending commands, and retrieving available
 * commands.
 *
 * <p>This interface abstracts the common functionalities required for interacting with Shelly
 * devices, making it easier to implement device-specific behavior while ensuring compatibility with
 * a common set of operations.
 */
public interface ShellyIfc {

  /**
   * Initializes the device or service on its first run. This method is called to perform any setup
   * or configuration tasks necessary for the device to function correctly.
   *
   * @param providerData the provider data that may contain specific configurations or parameters
   *     for the device.
   * @param resourceBundle the resource bundle used to provide localized messages or configurations.
   * @throws IOException if an I/O error occurs during initialization.
   * @throws InterruptedException if the initialization process is interrupted.
   */
  void doOnFirstRun(ProviderData providerData, ResourceBundle resourceBundle)
      throws IOException, InterruptedException;

  /**
   * Retrieves the list of available commands that can be executed by the device or service.
   *
   * @return a list of {@link Command} objects representing the available commands.
   */
  List<Command> getAvailableCommands();

  /**
   * Works with a given command property and set of variables. This method is responsible for
   * processing command properties and performing necessary actions based on the provided variables.
   *
   * @param httpConnection the http connection
   * @param property the command property to be worked on.
   * @param variables a map of variables used in the command processing.
   * @throws IOException if an I/O error occurs while processing the command.
   * @throws InterruptedException if the command processing is interrupted.
   */
  void workCommandProperty(
      HttpConnection httpConnection,
      CommandProviderProperty property,
      Map<String, Object> variables)
      throws IOException, InterruptedException;

  /**
   * Sets the settings for the device or service.
   *
   * @param setting the {@link Setting} object containing the configuration settings to be applied.
   */
  void setSetting(Setting setting);

  /**
   * Sends a command to the device or service for execution.
   *
   * @param httpConnection the http connection
   * @param command the {@link SendCommand} to be sent.
   * @throws IOException if an I/O error occurs while sending the command.
   * @throws InterruptedException if the command sending process is interrupted.
   */
  void sendCommand(HttpConnection httpConnection, SendCommand command)
      throws IOException, InterruptedException;

  /**
   * Analyzes the given list of provider fields and returns a list of tables containing analyzed
   * field data. This method is responsible for processing the provided fields and converting them
   * into a structured table format.
   *
   * @param providerFields a list of {@link PropertyField} objects to be analyzed.
   * @return a list of {@link Table} objects representing the analyzed data in a tabular form.
   */
  List<Table> analyzeTableFields(List<PropertyField> providerFields);

  /**
   * Analyzes the available commands based on the given provider fields and resource bundle. This
   * method processes the provider fields and returns a list of commands that are available for
   * execution.
   *
   * @param providerFields a list of {@link PropertyField} objects used to determine available
   *     commands.
   * @param resourceBundle the resource bundle containing localized data used during the analysis.
   * @return a list of {@link Command} objects representing the available commands.
   */
  List<Command> analyzeAvailableCommands(
      List<PropertyField> providerFields, ResourceBundle resourceBundle);
}
