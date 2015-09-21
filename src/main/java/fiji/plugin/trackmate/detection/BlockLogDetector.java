package fiji.plugin.trackmate.detection;

import java.util.Arrays;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class BlockLogDetector< T extends RealType< T > & NativeType< T >> extends LogDetector< T >
{
	/*
	 * CONSTANTS
	 */

	public final static String BASE_ERROR_MESSAGE = "BlockLogDetector: ";

	private final int nsplit;

	/*
	 * CONSTRUCTOR
	 */

	public BlockLogDetector( final RandomAccessible< T > img, final Interval interval, final double[] calibration, final double radius, final double threshold, final boolean doSubPixelLocalization, final boolean doMedianFilter, final int nsplit )
	{
		super( img, interval, calibration, radius, threshold, doSubPixelLocalization, doMedianFilter );
		this.nsplit = nsplit;
		this.baseErrorMessage = BASE_ERROR_MESSAGE;
	}

	/*
	 * METHODS
	 */

	@Override
	public boolean checkInput()
	{
		if ( nsplit < 1 )
		{
			errorMessage = baseErrorMessage + "N-split cannot be lower than 1. got " + nsplit + ".";
			return false;
		}
		return super.checkInput();
	}

	@Override
	public boolean process()
	{
		for ( int ix = 0; ix < nsplit; ix++ )
		{
			for ( int iy = 0; iy < nsplit; iy++ )
			{

				final Interval currentInterval = getBlock( ix, iy );
				final LogDetector< T > logDetector = new LogDetector< T >( img, currentInterval,
						calibration, radius, threshold, doSubPixelLocalization, doMedianFilter );
				logDetector.setNumThreads( numThreads );
				if ( !logDetector.checkInput() || !logDetector.process() )
				{
					errorMessage = BASE_ERROR_MESSAGE + "block ix=" + ix + ", iy=" + iy + " " + logDetector.errorMessage;
					return false;
				}
				spots.addAll( logDetector.getResult() );
			}
		}
		return true;
	}

	protected Interval getBlock( final int ix, final int iy )
	{
		return getBlock( interval, nsplit, ix, iy );
	}

	public static final Interval getBlock( final Interval interval, final int nsplit, final int ix, final int iy )
	{
		final int[] index = new int[] { ix, iy };

		final long[] min = new long[ interval.numDimensions() ];
		interval.min( min );

		final long[] size = new long[ interval.numDimensions() ];
		interval.dimensions( size );

		final long blockSize[] = Arrays.copyOf( size, size.length );
		for ( int d = 0; d < 2; d++ )
		{
			// Only split along X & Y
			blockSize[ d ] = interval.dimension( d ) / nsplit;
		}

		final long[] blockMin = Arrays.copyOf( min, min.length );
		for ( int d = 0; d < 2; d++ )
		{
			blockMin[ d ] = min[ d ] + index[ d ] * blockSize[ d ];
		}

		for ( int d = 0; d < 2; d++ )
		{
			// Add missing pixels for last blocks
			if ( index[ d ] == nsplit - 1 )
			{
				blockSize[ d ] += interval.dimension( d ) % nsplit;
			}
		}
		final long[] blockMax = new long[ interval.numDimensions() ];
		for ( int d = 0; d < blockMax.length; d++ )
		{
			blockMax[ d ] = blockMin[ d ] + blockSize[ d ] - 1; // inclusive
		}

		return new FinalInterval( blockMin, blockMax );
	}
}
