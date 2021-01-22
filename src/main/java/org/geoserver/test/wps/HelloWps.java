/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.geoserver.test.wps;

import org.geoserver.wps.gs.GeoServerProcess;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;

/**
 *
 * @author asrofiridho
 */
@DescribeProcess(title = "helloWPS", description = "Hello WPS Sample")
public class HelloWps implements GeoServerProcess {

    @DescribeResult(name = "result", description = "output result")
    public String execute(@DescribeParameter(name = "name", description = "name to return") String name) {
        return "Hello, " + name;
    }
}
