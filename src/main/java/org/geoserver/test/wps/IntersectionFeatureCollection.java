// 
// Decompiled by Procyon v0.5.36
// 
package org.geoserver.test.wps;

import java.util.ArrayList;
import org.opengis.feature.Feature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.Expression;
import org.geotools.util.factory.Hints;
import org.geotools.factory.CommonFactoryFinder;
import java.util.NoSuchElementException;
import org.locationtech.jts.geom.GeometryFilter;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.type.FeatureType;
import org.geotools.data.simple.SimpleFeatureIterator;
import java.util.Iterator;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.collection.DecoratingSimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.geotools.feature.AttributeTypeBuilder;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.type.AttributeDescriptor;
import java.util.logging.Level;
import org.opengis.referencing.operation.TransformException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.locationtech.jts.geom.Geometry;
import org.geotools.process.factory.DescribeResult;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.MultiPolygon;
import org.opengis.feature.simple.SimpleFeatureType;
import java.util.List;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.data.simple.SimpleFeatureCollection;
import java.util.logging.Logger;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.vector.VectorProcess;

//@DescribeProcess(title = "Intersection of Feature Collections", description = "Spatial intersection of two feature collections, including combining attributes from both.")
public class IntersectionFeatureCollection implements VectorProcess {

    private static final Logger logger;
    static final String ECKERT_IV_WKT = "PROJCS[\"World_Eckert_IV\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Eckert_IV\"],PARAMETER[\"Central_Meridian\",0.0],UNIT[\"Meter\",1.0]]";

    @DescribeResult(description = "Output feature collection")
    public SimpleFeatureCollection execute(@DescribeParameter(name = "first feature collection", description = "First feature collection")
            final SimpleFeatureCollection firstFeatures, 
            @DescribeParameter(name = "second feature collection", description = "Second feature collection") final SimpleFeatureCollection secondFeatures,
            @DescribeParameter(name = "first attributes to retain", collectionType = String.class, min = 0, description = "First feature collection attribute to include") final List<String> firstAttributes, 
            @DescribeParameter(name = "second attributes to retain", collectionType = String.class, min = 0, description = "Second feature collection attribute to include") final List<String> sndAttributes, 
            @DescribeParameter(name = "intersectionMode", min = 0, description = "Specifies geometry computed for intersecting features.  INTERSECTION (default) computes the spatial intersection of the inputs. FIRST copies geometry A.  SECOND copies geometry B.", defaultValue = "INTERSECTION") IntersectionMode intersectionMode, 
            @DescribeParameter(name = "percentagesEnabled", min = 0, description = "Indicates whether to output feature area percentages (attributes percentageA and percentageB)") Boolean percentagesEnabled, @DescribeParameter(name = "areasEnabled", min = 0, description = "Indicates whether to output feature areas (attributes areaA and areaB)") Boolean areasEnabled) {
        
        
        IntersectionFeatureCollection.logger.fine("INTERSECTION FEATURE COLLECTION WPS STARTED");
        if (percentagesEnabled == null) {
            percentagesEnabled = false;
        }
        if (areasEnabled == null) {
            areasEnabled = false;
        }
        if (intersectionMode == null) {
            intersectionMode = IntersectionMode.INTERSECTION;
        }
        final Class firstGeomType = ((SimpleFeatureType) firstFeatures.getSchema()).getGeometryDescriptor().getType().getBinding();
        final Class secondGeomType = ((SimpleFeatureType) secondFeatures.getSchema()).getGeometryDescriptor().getType().getBinding();
        if ((percentagesEnabled || areasEnabled) && (!isGeometryTypeIn(firstGeomType, MultiPolygon.class, Polygon.class) || !isGeometryTypeIn(secondGeomType, MultiPolygon.class, Polygon.class))) {
            throw new IllegalArgumentException("In case of opMode or areaMode are true, the features in the first and second collection must be polygonal");
        }
        if (!isGeometryTypeIn(firstGeomType, MultiPolygon.class, Polygon.class, MultiLineString.class, LineString.class)) {
            throw new IllegalArgumentException("First feature collection must be polygonal or linear");
        }
        return (SimpleFeatureCollection) new IntersectedFeatureCollection(firstFeatures, firstAttributes, secondFeatures, sndAttributes, intersectionMode, percentagesEnabled, areasEnabled);
    }

    static boolean isGeometryTypeIn(final Class test, final Class... targets) {
        for (final Class target : targets) {
            if (target.isAssignableFrom(test)) {
                return true;
            }
        }
        return false;
    }

    static Geometry densify(final Geometry geom, final CoordinateReferenceSystem crs, final double maxAreaError) throws FactoryException, TransformException {
        if (maxAreaError <= 0.0) {
            throw new IllegalArgumentException("maxAreaError must be greater than 0");
        }
        if (!(geom instanceof Polygon) && !(geom instanceof MultiPolygon)) {
            throw new IllegalArgumentException("Geom must be poligonal");
        }
        if (crs == null) {
            throw new IllegalArgumentException("CRS cannot be set to null");
        }
        double previousArea = 0.0;
        final CoordinateReferenceSystem targetCRS = CRS.parseWKT("PROJCS[\"World_Eckert_IV\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Eckert_IV\"],PARAMETER[\"Central_Meridian\",0.0],UNIT[\"Meter\",1.0]]");
        final MathTransform firstTransform = CRS.findMathTransform(crs, targetCRS);
        final GeometryFactory geomFactory = new GeometryFactory();
        final int ngeom = geom.getNumGeometries();
        Geometry densifiedGeometry = geom;
        double areaError = 1.0;
        int maxIterate = 0;
        do {
            double max = 0.0;
            ++maxIterate;
            for (int j = 0; j < ngeom; ++j) {
                final Geometry geometry = densifiedGeometry.getGeometryN(j);
                final Coordinate[] coordinates = geometry.getCoordinates();
                for (int n = coordinates.length, i = 0; i < n - 1; ++i) {
                    final Coordinate[] coords = {coordinates[i], coordinates[i + 1]};
                    final LineString lineString = geomFactory.createLineString(coords);
                    if (lineString.getLength() > max) {
                        max = lineString.getLength();
                    }
                }
            }
            densifiedGeometry = Densifier.densify(densifiedGeometry, max / 2.0);
            final Geometry targetGeometry = JTS.transform(densifiedGeometry, firstTransform);
            final double nextArea = targetGeometry.getArea();
            areaError = Math.abs(previousArea - nextArea) / nextArea;
            previousArea = nextArea;
        } while (areaError > maxAreaError && maxIterate < 10);
        return densifiedGeometry;
    }

    static double getIntersectionArea(final Geometry first, final CoordinateReferenceSystem firstCRS, final Geometry second, final CoordinateReferenceSystem secondCRS, final boolean divideFirst) {
        if (firstCRS == null || secondCRS == null) {
            throw new IllegalArgumentException("CRS cannot be set to null");
        }
        if (!Polygon.class.isAssignableFrom(first.getClass()) && !MultiPolygon.class.isAssignableFrom(first.getClass())) {
            throw new IllegalArgumentException("first geometry must be poligonal");
        }
        if (!Polygon.class.isAssignableFrom(second.getClass()) && !MultiPolygon.class.isAssignableFrom(second.getClass())) {
            throw new IllegalArgumentException("second geometry must be poligonal");
        }
        try {
            final Geometry firstTargetGeometry = reprojectAndDensify(first, firstCRS, null);
            final Geometry secondTargetGeometry = reprojectAndDensify(second, firstCRS, null);
            final double numeratorArea = firstTargetGeometry.intersection(secondTargetGeometry).getArea();
            if (divideFirst) {
                final double denom = firstTargetGeometry.getArea();
                if (denom != 0.0) {
                    return numeratorArea / denom;
                }
                return 0.0;
            } else {
                final double denom = secondTargetGeometry.getArea();
                if (denom != 0.0) {
                    return numeratorArea / denom;
                }
                return 0.0;
            }
        } catch (Exception e) {
            Logger.getGlobal().log(Level.INFO, "", e);
            return -1.0;
        }
    }

    static Geometry reprojectAndDensify(final Geometry first, final CoordinateReferenceSystem sourceCRS, CoordinateReferenceSystem targetCRS) throws FactoryException, TransformException {
        if (targetCRS == null) {
            targetCRS = CRS.parseWKT("PROJCS[\"World_Eckert_IV\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Eckert_IV\"],PARAMETER[\"Central_Meridian\",0.0],UNIT[\"Meter\",1.0]]");
        }
        final MathTransform firstTransform = CRS.findMathTransform(sourceCRS, targetCRS);
        final Geometry geometry = JTS.transform(densify(first, sourceCRS, 0.01), firstTransform);
        return geometry;
    }

    static AttributeDescriptor getIntersectionType(final SimpleFeatureCollection first, final SimpleFeatureCollection second) {
        final Class firstGeomType = ((SimpleFeatureType) first.getSchema()).getGeometryDescriptor().getType().getBinding();
        final Class secondGeomType = ((SimpleFeatureType) second.getSchema()).getGeometryDescriptor().getType().getBinding();
        Class binding;
        if (isGeometryTypeIn(secondGeomType, Point.class)) {
            binding = Point.class;
        } else if (isGeometryTypeIn(secondGeomType, MultiPoint.class)) {
            binding = MultiPoint.class;
        } else if (isGeometryTypeIn(secondGeomType, LineString.class, MultiLineString.class)) {
            binding = MultiLineString.class;
        } else if (isGeometryTypeIn(secondGeomType, Polygon.class, MultiPolygon.class)) {
            if (isGeometryTypeIn(firstGeomType, Polygon.class, MultiPolygon.class)) {
                binding = MultiPolygon.class;
            } else {
                binding = MultiLineString.class;
            }
        } else {
            binding = Geometry.class;
        }
        final AttributeTypeBuilder builder = new AttributeTypeBuilder();
        builder.setName("the_geom");
        builder.setBinding(binding);
        builder.setCRS(((SimpleFeature) first.features().next()).getFeatureType().getCoordinateReferenceSystem());
        final AttributeDescriptor descriptor = builder.buildDescriptor("the_geom");
        return descriptor;
    }

    static {
        logger = Logger.getLogger("org.geotools.process.feature.gs.IntersectionFeatureCollection");
    }

    public enum IntersectionMode {
        INTERSECTION,
        FIRST,
        SECOND;
    }

    static class IntersectedFeatureCollection extends DecoratingSimpleFeatureCollection {

        SimpleFeatureCollection features;
        List<String> firstAttributes;
        List<String> sndAttributes;
        IntersectionMode intersectionMode;
        boolean percentagesEnabled;
        boolean areasEnabled;
        SimpleFeatureBuilder fb;
        AttributeDescriptor geomType;

        public SimpleFeatureType getSchema() {
            return this.fb.getFeatureType();
        }

        public IntersectedFeatureCollection(final SimpleFeatureCollection delegate, final List<String> firstAttributes, final SimpleFeatureCollection features, final List<String> sndAttributes, final IntersectionMode intersectionMode, final boolean percentagesEnabled, final boolean areasEnabled) {
            super(delegate);
            this.firstAttributes = null;
            this.sndAttributes = null;
            this.geomType = null;
            this.features = features;
            this.firstAttributes = firstAttributes;
            this.sndAttributes = sndAttributes;
            this.intersectionMode = intersectionMode;
            this.percentagesEnabled = percentagesEnabled;
            this.areasEnabled = areasEnabled;
            final SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
            final SimpleFeatureType firstFeatureCollectionSchema = (SimpleFeatureType) delegate.getSchema();
            final SimpleFeatureType secondFeatureCollectionSchema = (SimpleFeatureType) features.getSchema();
            if (intersectionMode == IntersectionMode.FIRST) {
                this.geomType = (AttributeDescriptor) firstFeatureCollectionSchema.getGeometryDescriptor();
            }
            if (intersectionMode == IntersectionMode.SECOND) {
                this.geomType = (AttributeDescriptor) secondFeatureCollectionSchema.getGeometryDescriptor();
            }
            if (intersectionMode == IntersectionMode.INTERSECTION) {
                this.geomType = IntersectionFeatureCollection.getIntersectionType(delegate, features);
            }
            tb.add(this.geomType);
            this.collectAttributes(firstFeatureCollectionSchema, firstAttributes, tb);
            this.collectAttributes(secondFeatureCollectionSchema, sndAttributes, tb);
            if (percentagesEnabled) {
                tb.add("percentageA", (Class) Double.class);
                tb.add("percentageB", (Class) Double.class);
            }
            if (areasEnabled) {
                tb.add("areaA", (Class) Double.class);
                tb.add("areaB", (Class) Double.class);
            }
            tb.add("INTERSECTION_ID", (Class) Integer.class);
            tb.setDescription(firstFeatureCollectionSchema.getDescription());
            tb.setCRS(firstFeatureCollectionSchema.getCoordinateReferenceSystem());
            tb.setAbstract(firstFeatureCollectionSchema.isAbstract());
            tb.setSuperType((SimpleFeatureType) firstFeatureCollectionSchema.getSuper());
            tb.setName(firstFeatureCollectionSchema.getName());
            this.fb = new SimpleFeatureBuilder(tb.buildFeatureType());
        }

        private void collectAttributes(final SimpleFeatureType schema, final List<String> retainedAttributes, final SimpleFeatureTypeBuilder tb) {
            for (final AttributeDescriptor descriptor : schema.getAttributeDescriptors()) {
                boolean isInRetainList = true;
                if (retainedAttributes != null) {
                    isInRetainList = retainedAttributes.contains(descriptor.getLocalName());
                    IntersectionFeatureCollection.logger.fine("Checking " + descriptor.getLocalName() + " --> " + isInRetainList);
                }
                if (isInRetainList) {
                    if (schema.getGeometryDescriptor() == descriptor) {
                        continue;
                    }
                    final AttributeTypeBuilder builder = new AttributeTypeBuilder();
                    builder.setName(schema.getName().getLocalPart() + "_" + descriptor.getName());
                    builder.setNillable(descriptor.isNillable());
                    builder.setBinding(descriptor.getType().getBinding());
                    builder.setMinOccurs(descriptor.getMinOccurs());
                    builder.setMaxOccurs(descriptor.getMaxOccurs());
                    builder.setDefaultValue(descriptor.getDefaultValue());
                    builder.setCRS(schema.getCoordinateReferenceSystem());
                    final AttributeDescriptor intersectionDescriptor = builder.buildDescriptor(schema.getName().getLocalPart() + "_" + descriptor.getName(), descriptor.getType());
                    tb.add(intersectionDescriptor);
                    tb.addBinding(descriptor.getType());
                }
            }
        }

        public SimpleFeatureIterator features() {
            return (SimpleFeatureIterator) new IntersectedFeatureIterator(this.delegate.features(), this.delegate, this.features, (SimpleFeatureType) this.delegate.getSchema(), (SimpleFeatureType) this.features.getSchema(), this.firstAttributes, this.sndAttributes, this.intersectionMode, this.percentagesEnabled, this.areasEnabled, this.fb);
        }
    }

    static class IntersectedFeatureIterator implements SimpleFeatureIterator {

        SimpleFeatureIterator delegate;
        SimpleFeatureCollection firstFeatures;
        SimpleFeatureCollection secondFeatures;
        SimpleFeatureCollection subFeatureCollection;
        SimpleFeatureBuilder fb;
        SimpleFeature next;
        SimpleFeature first;
        Integer iterationIndex;
        boolean complete;
        boolean added;
        SimpleFeatureCollection intersectedGeometries;
        SimpleFeatureIterator iterator;
        String dataGeomName;
        List<String> retainAttributesFst;
        List<String> retainAttributesSnd;
        AttributeDescriptor geomType;
        boolean percentagesEnabled;
        boolean areasEnabled;
        IntersectionMode intersectionMode;
        int id;

        public IntersectedFeatureIterator(final SimpleFeatureIterator delegate, final SimpleFeatureCollection firstFeatures, final SimpleFeatureCollection secondFeatures, final SimpleFeatureType firstFeatureCollectionSchema, final SimpleFeatureType secondFeatureCollectionSchema, final List<String> retainAttributesFstPar, final List<String> retainAttributesSndPar, final IntersectionMode intersectionMode, final boolean percentagesEnabled, final boolean areasEnabled, final SimpleFeatureBuilder sfb) {
            this.iterationIndex = 0;
            this.complete = true;
            this.added = false;
            this.retainAttributesFst = null;
            this.retainAttributesSnd = null;
            this.geomType = null;
            this.id = 0;
            this.retainAttributesFst = retainAttributesFstPar;
            this.retainAttributesSnd = retainAttributesSndPar;
            this.delegate = delegate;
            this.firstFeatures = firstFeatures;
            this.secondFeatures = secondFeatures;
            this.percentagesEnabled = percentagesEnabled;
            this.areasEnabled = areasEnabled;
            this.intersectionMode = intersectionMode;
            IntersectionFeatureCollection.logger.fine("Creating schema");
            if (intersectionMode == IntersectionMode.FIRST) {
                this.geomType = (AttributeDescriptor) firstFeatureCollectionSchema.getGeometryDescriptor();
            }
            if (intersectionMode == IntersectionMode.SECOND) {
                this.geomType = (AttributeDescriptor) secondFeatureCollectionSchema.getGeometryDescriptor();
            }
            if (intersectionMode == IntersectionMode.INTERSECTION) {
                this.geomType = IntersectionFeatureCollection.getIntersectionType(firstFeatures, secondFeatures);
            }
            this.fb = sfb;
            this.subFeatureCollection = this.secondFeatures;
            this.dataGeomName = ((SimpleFeatureType) this.firstFeatures.getSchema()).getGeometryDescriptor().getLocalName();
            IntersectionFeatureCollection.logger.fine("Schema created");
        }

        public void close() {
            this.delegate.close();
        }

        public boolean hasNext() {
            IntersectionFeatureCollection.logger.finer("HAS NEXT");
            while ((this.next == null && this.delegate.hasNext()) || (this.next == null && this.added)) {
                if (this.complete) {
                    this.first = (SimpleFeature) this.delegate.next();
                    this.intersectedGeometries = null;
                }
                for (Object attribute : this.first.getAttributes()) {
                    if (attribute instanceof Geometry && attribute.equals(this.first.getDefaultGeometry())) {
                        final Geometry currentGeom = (Geometry) attribute;
                        if (this.intersectedGeometries == null && !this.added) {
                            this.intersectedGeometries = this.filteredCollection(currentGeom, this.subFeatureCollection);
                            this.iterator = this.intersectedGeometries.features();
                        }
                        try {
                            while (this.iterator.hasNext()) {
                                this.added = false;
                                final SimpleFeature second = (SimpleFeature) this.iterator.next();
                                if (currentGeom.getEnvelope().intersects((Geometry) second.getDefaultGeometry())) {
                                    if (this.intersectionMode == IntersectionMode.INTERSECTION) {
                                        attribute = currentGeom.intersection((Geometry) second.getDefaultGeometry());
                                        final GeometryFilterImpl filter = new GeometryFilterImpl(this.geomType.getType().getBinding());
                                        ((Geometry) attribute).apply((GeometryFilter) filter);
                                        attribute = filter.getGeometry();
                                    } else if (this.intersectionMode == IntersectionMode.FIRST) {
                                        attribute = currentGeom;
                                    } else if (this.intersectionMode == IntersectionMode.SECOND) {
                                        attribute = second.getDefaultGeometry();
                                    }
                                    if (((Geometry) attribute).getNumGeometries() > 0) {
                                        this.fb.add(attribute);
                                        this.fb.set("INTERSECTION_ID", (Object) (this.id++));
                                        this.addAttributeValues(this.first, this.retainAttributesFst, this.fb);
                                        this.addAttributeValues(second, this.retainAttributesSnd, this.fb);
                                        if (this.percentagesEnabled) {
                                            this.addPercentages(currentGeom, second);
                                        }
                                        if (this.areasEnabled) {
                                            this.addAreas(currentGeom, second);
                                        }
                                        this.next = this.fb.buildFeature(this.iterationIndex.toString());
                                        if (this.iterator.hasNext()) {
                                            this.complete = false;
                                            this.added = true;
                                            final Integer iterationIndex = this.iterationIndex;
                                            ++this.iterationIndex;
                                            return this.next != null;
                                        }
                                        final Integer iterationIndex2 = this.iterationIndex;
                                        ++this.iterationIndex;
                                    }
                                }
                                this.complete = false;
                            }
                            this.complete = true;
                        } finally {
                            if (!this.added) {
                                this.iterator.close();
                            }
                        }
                    }
                }
            }
            return this.next != null;
        }

        private void addAttributeValues(final SimpleFeature feature, final List<String> retained, final SimpleFeatureBuilder fb) {
            for (final AttributeDescriptor ad : feature.getType().getAttributeDescriptors()) {
                final Object firstAttribute = feature.getAttribute(ad.getLocalName());
                if ((retained == null || retained.contains(ad.getLocalName())) && !(firstAttribute instanceof Geometry)) {
                    fb.add(feature.getAttribute(ad.getLocalName()));
                }
            }
        }

        private void addAreas(final Geometry currentGeom, final SimpleFeature second) {
            final CoordinateReferenceSystem firstCRS = ((SimpleFeatureType) this.firstFeatures.getSchema()).getCoordinateReferenceSystem();
            final CoordinateReferenceSystem secondCRS = ((SimpleFeatureType) this.secondFeatures.getSchema()).getCoordinateReferenceSystem();
            try {
                final double areaA = IntersectionFeatureCollection.reprojectAndDensify(currentGeom, firstCRS, null).getArea();
                final double areaB = IntersectionFeatureCollection.reprojectAndDensify((Geometry) second.getDefaultGeometry(), secondCRS, null).getArea();
                this.fb.set("areaA", (Object) areaA);
                this.fb.set("areaB", (Object) areaB);
            } catch (Exception e) {
                IntersectionFeatureCollection.logger.log(Level.FINE, "", e);
                this.fb.set("areaA", (Object) (-1));
                this.fb.set("areaB", (Object) (-1));
            }
        }

        private void addPercentages(final Geometry currentGeom, final SimpleFeature second) {
            final CoordinateReferenceSystem firstCRS = ((SimpleFeatureType) this.firstFeatures.getSchema()).getCoordinateReferenceSystem();
            final CoordinateReferenceSystem secondCRS = ((SimpleFeatureType) this.secondFeatures.getSchema()).getCoordinateReferenceSystem();
            final double percentageA = IntersectionFeatureCollection.getIntersectionArea(currentGeom, firstCRS, (Geometry) second.getDefaultGeometry(), secondCRS, true);
            final double percentageB = IntersectionFeatureCollection.getIntersectionArea(currentGeom, firstCRS, (Geometry) second.getDefaultGeometry(), secondCRS, false);
            this.fb.set("percentageA", (Object) percentageA);
            this.fb.set("percentageB", (Object) percentageB);
        }

        public SimpleFeature next() throws NoSuchElementException {
            if (!this.hasNext()) {
                throw new NoSuchElementException("hasNext() returned false!");
            }
            final SimpleFeature result = this.next;
            this.next = null;
            return result;
        }

        private SimpleFeatureCollection filteredCollection(final Geometry currentGeom, final SimpleFeatureCollection subFeatureCollection) {
            final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2((Hints) null);
            final Filter intersectFilter = (Filter) ff.intersects((Expression) ff.property(this.dataGeomName), (Expression) ff.literal((Object) currentGeom));
            SimpleFeatureCollection subFeatureCollectionIntersection = this.subFeatureCollection.subCollection(intersectFilter);
            if (subFeatureCollectionIntersection.size() == 0) {
                subFeatureCollectionIntersection = subFeatureCollection;
            }
            return subFeatureCollectionIntersection;
        }
    }

    static class GeometryFilterImpl implements GeometryFilter {

        GeometryFactory factory;
        ArrayList<Geometry> collection;
        Class binding;

        GeometryFilterImpl(final Class binding) {
            this.factory = new GeometryFactory();
            this.collection = new ArrayList<Geometry>();
            this.binding = null;
            this.binding = binding;
        }

        public void filter(final Geometry gmtr) {
            if (MultiPolygon.class.isAssignableFrom(this.binding) && gmtr.getArea() != 0.0 && gmtr.getGeometryType().equals("Polygon")) {
                this.collection.add(gmtr);
            }
            if (MultiLineString.class.isAssignableFrom(this.binding) && gmtr.getLength() != 0.0 && gmtr.getGeometryType().equals("LineString")) {
                this.collection.add(gmtr);
            }
            if (MultiPoint.class.isAssignableFrom(this.binding) && gmtr.getNumGeometries() > 0 && gmtr.getGeometryType().equals("Point")) {
                this.collection.add(gmtr);
            }
            if (Point.class.isAssignableFrom(this.binding) && gmtr.getGeometryType().equals("Point")) {
                this.collection.add(gmtr);
            }
        }

        public Geometry getGeometry() {
            final int n = this.collection.size();
            if (MultiPolygon.class.isAssignableFrom(this.binding)) {
                final Polygon[] array = new Polygon[n];
                for (int i = 0; i < n; ++i) {
                    array[i] = (Polygon) this.collection.get(i);
                }
                return (Geometry) this.factory.createMultiPolygon(array);
            }
            if (MultiLineString.class.isAssignableFrom(this.binding)) {
                final LineString[] array2 = new LineString[n];
                for (int i = 0; i < n; ++i) {
                    array2[i] = (LineString) this.collection.get(i);
                }
                return (Geometry) this.factory.createMultiLineString(array2);
            }
            if (MultiPoint.class.isAssignableFrom(this.binding)) {
                final Point[] array3 = new Point[n];
                for (int i = 0; i < n; ++i) {
                    array3[i] = (Point) this.collection.get(i);
                }
                return (Geometry) this.factory.createMultiPoint(array3);
            }
            return null;
        }
    }
}
