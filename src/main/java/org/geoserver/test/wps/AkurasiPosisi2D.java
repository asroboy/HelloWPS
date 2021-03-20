/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.geoserver.test.wps;

import java.io.IOException;
import java.io.Serializable;
import java.math.RoundingMode;
import java.text.DecimalFormat;
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
@DescribeProcess(title = "AkurasiPosisi2D", description = "Menghitung akurasi posisi 2 Dimensi / CE90")
public class AkurasiPosisi2D implements GeoServerProcess {

    DataStore dataStore = null;

    @DescribeResult(name = "Hasil", description = "Hasil hitungan akuarasi posisi")
    public String execute(
            @DescribeParameter(name = "Data Pengukuran (service)", description = "Data Pengukuran") SimpleFeatureCollection firstFeatures,
            @DescribeParameter(name = "Data Peta (service)", description = "Data Peta") SimpleFeatureCollection secondFeatures) throws IOException {

        String txtData = "------------------------Data ------------------------";
        double sumdxsqdysq = 0.0;
        
        DecimalFormat df = new DecimalFormat("#.####");
        df.setRoundingMode(RoundingMode.CEILING);

        DecimalFormat dfGeom = new DecimalFormat("#.##########");
        dfGeom.setRoundingMode(RoundingMode.CEILING);

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
//                      System.out.println("X " + geom2.getCoordinate().x);
//                      System.out.println("Y " + geom2.getCoordinate().y);
//                      System.out.println("Z " + geom2.getCoordinate().z);
                        String fid2 = f2.getID();
                        int id2 = Integer.parseInt(fid2.split("\\.")[1]);
//                      System.out.print("\nID " +id);
//                      System.out.print("\n ID 2 " + id2);
                        if (id == id2) {
                            double dx = geom2.getX() - geom.getX();
                            double dy = geom2.getY() - geom.getY();
                            double dxsq = Math.pow(dx, 2);
                            double dysq = Math.pow(dy, 2);
                            double dxsq_dysq = dysq + dxsq;
                            sumdxsqdysq += dxsq_dysq;
//                            txtData += String.format("\nx %f y %f, x2 %f y2 %f, Dx %f Dy %f dxsq %f dysq %f dxsq + dysq %f",
//                                    geom.getX(), geom.getY(), geom2.getX(), geom.getY(), dx, dy, dxsq, dysq, dxsq_dysq);
//                       
                            txtData += "\nx " + dfGeom.format(geom.getX()) + " y " +  dfGeom.format(geom.getY()) +
                                        " x2 " +  dfGeom.format(geom2.getX()) + " y2 " + dfGeom.format(geom2.getY()) +
                                        " Dx " +  dfGeom.format(dx) + " Dy " + dfGeom.format(dy) + 
                                        " dxsq " +  dfGeom.format(dxsq) + " dysq " + dfGeom.format(dysq) + 
                                        " dxsq + dysq " +  dfGeom.format(dxsq_dysq);
                        }
//                      System.out.println("Geom : " + geom.toText());
                    }
                }
            }
        }

        txtData += "\n\n----------result-----------\n";
        txtData += "\nsum of dxsq + dysq : " +  df.format(sumdxsqdysq);
        double rataRata = sumdxsqdysq / firstFeatures.size();
        txtData += "\nsize data : " + String.valueOf(firstFeatures.size());
        txtData += "\navg of (sum dxsq + dysq) : " + df.format(rataRata);
        double RMSE = Math.pow(rataRata, 0.5);
        txtData += "\nRMSE : " + df.format(RMSE);
        double akurasi = 1.5175 * RMSE;
        txtData += "\naccuration : " + df.format(akurasi);

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

    public AkurasiPosisi2D(String capabilities) {
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
