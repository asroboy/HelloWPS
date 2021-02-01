/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.geoserver.test.wps;

import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.wps.gs.GeoServerProcess;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.FileDataStoreFactorySpi;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.shapefile.shp.ShapefileWriter;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.feature.SchemaException;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.vector.IntersectionFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 *
 * @author asrofiridho
 */

@DescribeProcess(title = "kelengkapanPolygon", description = "Menghitung Omisi dan Komisi")
public class KelengkapanPolygon implements GeoServerProcess{
    
    DataStore dataStore = null;

    
    public static void main(String argv[]){
        try {
            execute();
        } catch (IOException ex) {
            Logger.getLogger(KelengkapanPolygon.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    @DescribeResult(name = "result", description = "Output / Hasil")
    public static String execute() throws IOException {
        String getCapabilities = "http://127.0.0.1:8080/geoserver/BIG_DEV/wms?service=WMS&version=1.1.0&&request=GetCapabilities";
        KelengkapanPolygon me = new KelengkapanPolygon(getCapabilities);
        String[] names = me.getTypeNames();
        for (String name : names) {
            SimpleFeatureType schema = me.getSchema(name);
            System.out.println(name + ":" + schema);
        }

        String typeName = "BIG_DEV:BANGUNAN_SEDANG_PY_REF";
        String typeName2 = "BIG_DEV:BANGUNAN_SEDANG_UJI";
        SimpleFeatureCollection firstFeatures = me.getFeatureCollection(typeName);
        SimpleFeatureCollection secondFeatures = me.getFeatureCollection(typeName2);

        IntersectionFeatureCollection ifc = new IntersectionFeatureCollection();
        SimpleFeatureCollection output = ifc.execute(firstFeatures, secondFeatures, null, null, IntersectionFeatureCollection.IntersectionMode.INTERSECTION, Boolean.FALSE, Boolean.TRUE);

        if (!output.isEmpty()) {
            SimpleFeature sf = output.features().next();
            Geometry geom = (Geometry) sf.getDefaultGeometry();
           System.out.println("Geom : " + geom.toText());

        }
//        ShapefileWriter sfw = new ShapefileWriter(shpChannel, shxChannel)
       
        GeoServerRESTPublisher gsrp = new GeoServerRESTPublisher("http://localhost:8080/geoserver", "adminbig", "adminbig");
       
        FileDataStoreFactorySpi factory = new ShapefileDataStoreFactory();

        File file = new File("my.shp");
        Map<String, ?> map = Collections.singletonMap("url", file.toURI().toURL());

        DataStore myData = factory.createNewDataStore((Map<String, Serializable>) map);
//        SimpleFeatureType featureType =
//                DataUtilities.createType(
//                        "my", "geom:Point,name:String,age:Integer,description:String");
        myData.createSchema(output.getSchema());
        
        gsrp.publishShp("BIG_DEV", "omisi", "omisi_dataset", file);

        return "Done";
    }

    private SimpleFeatureCollection getFeatureCollection(String typeName) throws IOException {
        return dataStore.getFeatureSource(typeName).getFeatures();
    }

    private String[] getTypeNames() throws IOException {
        return dataStore.getTypeNames();
    }

    private SimpleFeatureType getSchema(String name) throws IOException {
        return dataStore.getSchema(name);
    }

    public KelengkapanPolygon(String capabilities) {
        aquireDataStoreWFS(capabilities);
    }

    public void aquireDataStoreWFS(String capabilities) {
        if (dataStore == null) {
            try {
                Map<String, Serializable> connectionParameters = new HashMap<>();
                connectionParameters.put(WFSDataStoreFactory.URL.key, capabilities);
                dataStore = DataStoreFinder.getDataStore(connectionParameters);
            } catch (IOException e) {
            }
        }
    }
}
