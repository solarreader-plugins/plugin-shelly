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
package de.schnippsche.solarreader.test;

import de.schnippsche.solarreader.backend.connection.network.HttpConnection;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

public class ShellyPlusHttpConnection implements HttpConnection {

  @Override
  public void test(URL url, String validMediaType) {}

  @Override
  public HttpRequest buildGetRequest(URL url, Map<String, String> headers) {
    return null;
  }

  @Override
  public HttpRequest buildPostRequest(URL url, Map<String, String> headers, String body) {
    return null;
  }

  @Override
  public HttpResponse<String> get(URL url) {
    return null;
  }

  @Override
  public String getAsString(URL url) {
    Path path;
    String fileName;
    if (url.toString().endsWith("/rpc/shelly.GetConfig")) {
      fileName = "shellyplus_config_response.json";
    } else if (url.toString().endsWith("/shelly")) {
      fileName = "shellyplus_shelly_response.json";
    } else if (url.toString().endsWith("/rpc/shelly.GetStatus")) {
      fileName = "shellyplus_status_response.json";
    } else if (url.toString().endsWith("/rpc/shelly.GetDeviceInfo")) {
      fileName = "shellyplus_deviceinfo_response.json";
    } else if (url.toString().endsWith("rpc/shelly.GetComponents")) {
      fileName = "shellyplus_components_response.json";
    } else if (url.toString().endsWith("rpc/sys.GetConfig")) {
      fileName = "shellyplus_sysconfig_response.json";
    } else if (url.toString().endsWith("rpc/sys.GetStatus")) {
      fileName = "shellyplus_sysstatus_response.json";

    } else {
      throw new IllegalArgumentException("Unsupported URL: " + url);
    }
    try {
      path =
          Paths.get(
              Objects.requireNonNull(getClass().getClassLoader().getResource(fileName)).toURI());
      return Files.readString(path);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public HttpResponse<String> post(URL url, Map<String, String> formData, String body) {
    return null;
  }

  @Override
  public HttpResponse<String> sendRequest(HttpRequest request) {
    return null;
  }
}
