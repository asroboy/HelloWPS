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
import org.geoserver.wps.gs.GeoServerProcess;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 *
 * @author asrofiridho
 */

@DescribeProcess(title = "AkurasiPosisi3D", description = "Menghitung akurasi posisi 3 Dimensi / CE90")
public class TingkatAkurasiWPSZ implements GeoServerProcess {

    DataStore dataStore = null;

    @DescribeResult(name = "result", description = "Output / Hasil")
    public String execute(
            @DescribeParameter(name = "Data Pengukuran", description = "Data Pengukuran")  SimpleFeatureCollection firstFeatures,
            @DescribeParameter(name = "Date Peta", description = "Data Peta") SimpleFeatureCollection secondFeatures) throws IOException {

//        String getCapabilities = urlService;
//        TingkatAkurasiWPSZ me = new TingkatAkurasiWPSZ(getCapabilities);
//        String[] names = me.getTypeNames();
//        for (String name : names) {
//            SimpleFeatureType schema = me.getSchema(name);
//            System.out.println(name + ":" + schema);
//        }
//
        String txtData = "------------------------Data ------------------------";
        double sumdxsqdysq = 0.0;
        double sumdzsq = 0.0;

//        SimpleFeatureCollection firstFeatures = me.getFeatureCollection(typeName);
//        SimpleFeatureCollection secondFeatures = me.getFeatureCollection(typeName2);
        try (SimpleFeatureIterator itr = firstFeatures.features()) {
            while (itr.hasNext()) {
                SimpleFeature f = itr.next();
                String fid = f.getID();
                Point geom = (Point) f.getDefaultGeometry();
                int id = Integer.parseInt(fid.split("\\.")[1]);
                try (SimpleFeatureIterator itr2 = secondFeatures.features()) {
                    while (itr2.hasNext()) {
                        SimpleFeature f2 = itr2.next();
                        Point geom2 = (Point) f2.getDefaultGeometry();
                        String fid2 = f2.getID();
                        int id2 = Integer.parseInt(fid2.split("\\.")[1]);
                        if (id == id2) {
                            double dx = geom2.getX() - geom.getX();
                            double dy = geom2.getY() - geom.getY();
                            double dz = geom2.getCoordinate().z - geom.getCoordinate().z;
                            double dxsq = Math.pow(dx, 2);
                            double dysq = Math.pow(dy, 2);
                            double dzsq = Math.pow(dz, 2);
                            double dxsq_dysq = dysq + dxsq;
                            sumdxsqdysq += dxsq_dysq;
                            sumdzsq += dzsq;
                            txtData += String.format("\nx %f y %f z %f, x2 %f y2 %f z2 %f, Dx %f Dy %f Dz %f dxsq %f dysq %f dzsq %f dxsq + dysq %f",
                                    geom.getX(), geom.getY(), geom.getCoordinate().z, geom2.getX(), geom.getY(), geom2.getCoordinate().z, dx, dy, dz, 
                                    dxsq, dysq, dzsq, dxsq_dysq);
                        }
//                      System.out.println("Geom : " + geom.toText());
                    }
                }
            }
        }

        txtData += "\n\n----------result horzontal (XY)-----------\n";
        txtData += "\nsum of dxsq + dysq : " + String.valueOf(sumdxsqdysq);
        double rataRata = sumdxsqdysq / firstFeatures.size();
        txtData += "\nsize data : " + String.valueOf(firstFeatures.size());
        txtData += "\navg of (sum dxsq + dysq) : " + String.valueOf(rataRata);
        double RMSE = Math.pow(rataRata, 0.5);
        txtData += "\nRMSE : " + String.valueOf(RMSE);
        double akurasi = 1.5175 * RMSE;
        txtData += "\naccuration : " + String.valueOf(akurasi);

        
        
        txtData += "\n\n----------result vertical (Z)-----------\n";
        txtData += "\nsum of dzsq : " + String.valueOf(sumdzsq);
        double  rataRataDz = sumdzsq / firstFeatures.size();
        txtData += "\navg of (sum dzsq) : " + String.valueOf(rataRataDz);
        double RMSEZ = Math.pow(rataRataDz, 0.5);
        double akurasiZ = 1.6449 * RMSEZ;
        txtData += "\nvertical accuration : " + String.valueOf(akurasiZ);
        
        
        return txtData;
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

    public TingkatAkurasiWPSZ(String capabilities) {
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
