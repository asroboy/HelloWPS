/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.geoserver.test.wps;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.process.vector.IntersectionFeatureCollection;
import org.geotools.process.vector.QueryProcess;
import org.geotools.process.vector.SimpleProcessingCollection;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 *
 * @author asrofiridho
 */
public final class GetWFSAttributes {

    DataStore dataStore = null;

    public static void main(String[] args) throws IOException {
        String getCapabilities = "http://127.0.0.1:8080/geoserver/BIG_DEV/wms?service=WMS&version=1.1.0&&request=GetCapabilities";
        GetWFSAttributes me = new GetWFSAttributes(getCapabilities);
        String[] names = me.getTypeNames();
        for (String name : names) {
            SimpleFeatureType schema = me.getSchema(name);
            System.out.println(name + ":" + schema);
        }

        String typeName = "BIG_DEV:BANGUNAN_SEDANG_PY_REF";
        String typeName2 = "BIG_DEV:BANGUNAN_SEDANG_UJI";
        SimpleFeatureCollection firstFeatures = me.getFeatureCollection(typeName);
        SimpleFeatureCollection secondFeatures = me.getFeatureCollection(typeName2);
//    try (SimpleFeatureIterator itr = features.features()) {
//      while (itr.hasNext()) {
//        SimpleFeature f = itr.next();
//        Geometry geom = (Geometry) f.getDefaultGeometry();
//        double lat = geom.getCentroid().getY();
//        double lon = geom.getCentroid().getX();
//        String name = (String) f.getAttribute("PELAKSANA");
//        String abbr = (String) f.getAttribute("NAMOBJ");
////        double people = (double) f.getAttribute("PERSONS");
////        System.out.println(name + "\t(" + abbr + ")\t" + "\t(" + lat + "," + lon + ")");
//
//        System.out.println("Geom : " + geom.toText());
//      }
//    }

        IntersectionFeatureCollection ifc = new IntersectionFeatureCollection();
        SimpleFeatureCollection output = ifc.execute(firstFeatures, secondFeatures, null, null, IntersectionFeatureCollection.IntersectionMode.INTERSECTION, Boolean.FALSE, Boolean.TRUE);

        if (!output.isEmpty()) {
            SimpleFeature sf = output.features().next();
            Geometry geom = (Geometry) sf.getDefaultGeometry();
           System.out.println("Geom : " + geom.toText());

        }
        
        
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

    public GetWFSAttributes(String capabilities) {
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
