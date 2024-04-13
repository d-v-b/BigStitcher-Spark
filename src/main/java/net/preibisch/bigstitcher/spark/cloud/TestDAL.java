package net.preibisch.bigstitcher.spark.cloud;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.opendal.Entry;
import org.apache.opendal.Operator;

import bdv.ViewerImgLoader;
import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.bigstitcher.spark.util.Spark;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorer;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointsN5;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;

/**
 * How to read/write: https://opendal.apache.org/docs/quickstart
 * How to use S3: https://opendal.apache.org/docs/services/s3
 * GitHub: https://github.com/apache/opendal
 * Supported architectures: https://repo1.maven.org/maven2/org/apache/opendal/opendal-java/0.45.1/
 */
public class TestDAL
{
	public static void fileSystem()
	{
		final Map<String, String> conf = new HashMap<>();
		conf.put("root", "/tmp");

		final Operator op = Operator.of("fs", conf );
		op.write( "hello2.txt", "hi" ).join();
		byte[] b = op.read( "hello2.txt" ).join();
		System.out.println( new String(b, StandardCharsets.UTF_8));
		
		op.close();
		
	}

	public static void awsS3() throws SpimDataException
	{
		final Map<String, String> builder = new HashMap<>();
		builder.put("root", "Stitching");
		builder.put("bucket", "janelia-bigstitcher-spark" );
		builder.put("region", "us-east-1");
		builder.put("endpoint", "https://s3.amazonaws.com");

		final Operator op = Operator.of("s3", builder );

		byte[] decodedBytes = op.read( "dataset.xml" ).join();
		System.out.println( new String(decodedBytes, StandardCharsets.UTF_8).substring( 0, 200 ) );

		System.out.println( "\ndataset.xml exists = " + CloudUtil.exists(op, "dataset.xml"));
		System.out.println( "dataset_2.xml exists = " + CloudUtil.exists(op, "dataset_2.xml"));

		System.out.println( "\nList of '" + builder.get( "root" ) + "'" );
		List<Entry> entries = op.list( "" ).join();
		entries.forEach( e -> System.out.println( e.getPath() + "\t" + e.metadata.getContentLength() ) );

		op.close();

		//SpimData2 data = Spark.getSparkJobSpimData2( "s3://janelia-bigstitcher-spark/Stitching/dataset.xml" );
		//System.out.println( data.getSequenceDescription().getViewSetupsOrdered().size() );
	}

	public static void testLoadInterestPoints() throws SpimDataException
	{
		final SpimData2 data = Spark.getSparkJobSpimData2( "s3://janelia-bigstitcher-spark/Stitching/dataset.xml" );

		System.out.println( "num viewsetups: " + data.getSequenceDescription().getViewSetupsOrdered().size() );

		final Map<ViewId, ViewInterestPointLists> ips = data.getViewInterestPoints().getViewInterestPoints();
		final ViewInterestPointLists ipl = ips.values().iterator().next();
		final InterestPoints ip = ipl.getHashMap().values().iterator().next();
		
		System.out.println("base dir: " + ip.getBaseDir() );
		System.out.println("base dir modified: " + InterestPointsN5.assembleURI( ip.getBaseDir(), InterestPointsN5.baseN5 ) );

		List<InterestPoint> ipList = ip.getInterestPointsCopy();

		System.out.println( "Loaded " + ipList.size() + " interest points.");

		//Spark.saveSpimData2( data, "s3://janelia-bigstitcher-spark/Stitching/dataset-save.xml" );
	}

	public static void testBigStitcherGUI() throws SpimDataException
	{
		new ImageJ();

		final String xml = "s3://janelia-bigstitcher-spark/Stitching/dataset.xml";

		final SpimData2 data = Spark.getSparkJobSpimData2( xml );

		final BasicImgLoader imgLoader = data.getSequenceDescription().getImgLoader();
		if (imgLoader instanceof ViewerImgLoader)
			((ViewerImgLoader) imgLoader).setNumFetcherThreads(-1);

		final ViewSetupExplorer< SpimData2 > explorer = new ViewSetupExplorer<>( data, xml, new XmlIoSpimData2("") );

		explorer.getFrame().toFront();
	}

	public static void main( String[] args ) throws SpimDataException
	{
		fileSystem();
		awsS3();
		testLoadInterestPoints();
		testBigStitcherGUI();
	}
}
