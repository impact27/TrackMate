package fiji.plugin.trackmate.visualization;

import ij.ImagePlus;
import ij.gui.NewImage;
import ij.gui.StackWindow;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import fiji.plugin.trackmate.Feature;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.SpotImp;
import fiji.util.gui.AbstractAnnotation;
import fiji.util.gui.OverlayedImageCanvas;

public class HyperStackDisplayer extends SpotDisplayer implements MouseListener {

	/*
	 * INNER CLASSES
	 */
	
	private class TrackOverlay extends AbstractAnnotation {
		protected ArrayList<Integer> X0 = new ArrayList<Integer>();
		protected ArrayList<Integer> Y0 = new ArrayList<Integer>();
		protected ArrayList<Integer> X1 = new ArrayList<Integer>();
		protected ArrayList<Integer> Y1 = new ArrayList<Integer>();
		protected ArrayList<Integer> frames = new ArrayList<Integer>();
		protected float lineThickness = 1.0f;

		public TrackOverlay(Color color) {
			this.color = color;
			this.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER);
		}

		@Override
		public void draw(Graphics2D g2d) {
			
			if (!trackVisible)
				return;
			
			g2d.setStroke(new BasicStroke((float) (lineThickness / canvas.getMagnification()),  BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			switch (trackDisplayMode) {

			case ALL_WHOLE_TRACKS:
				for (int i = 0; i < frames.size(); i++) 
					g2d.drawLine(X0.get(i), Y0.get(i), X1.get(i), Y1.get(i));
				break;

			case LOCAL_WHOLE_TRACKS: {
				final int currentFrame = imp.getFrame()-1;
				int frameDist;
				for (int i = 0; i < frames.size(); i++) {
					frameDist = Math.abs(frames.get(i) - currentFrame); 
					if (frameDist > trackDisplayDepth)
						continue;
					g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1 - (float) frameDist / trackDisplayDepth));
					g2d.drawLine(X0.get(i), Y0.get(i), X1.get(i), Y1.get(i));
				}				
				break;
			}

			case LOCAL_FORWARD_TRACKS: {
				final int currentFrame = imp.getFrame()-1;
				int frameDist;
				for (int i = 0; i < frames.size(); i++) {
					frameDist = frames.get(i) - currentFrame; 
					if (frameDist < 0 || frameDist > trackDisplayDepth)
						continue;
					g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1 - (float) frameDist / trackDisplayDepth));
					g2d.drawLine(X0.get(i), Y0.get(i), X1.get(i), Y1.get(i));
				}
				break;
			}

			case LOCAL_BACKWARD_TRACKS: {
				final int currentFrame = imp.getFrame()-1;
				int frameDist;
				for (int i = 0; i < frames.size(); i++) {
					frameDist = currentFrame - frames.get(i); 
					if (frameDist < 0 || frameDist > trackDisplayDepth)
						continue;
					g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1  - (float) frameDist / trackDisplayDepth));
					g2d.drawLine(X0.get(i), Y0.get(i), X1.get(i), Y1.get(i));
				}
				break;
			}

			}

		}
		
		public void addEdge(final Spot source, final Spot target, final int frame) {
			X0.add(Math.round(source.getFeature(Feature.POSITION_X) / calibration[0]) );
			Y0.add(Math.round(source.getFeature(Feature.POSITION_Y) / calibration[1]) );
			X1.add(Math.round(target.getFeature(Feature.POSITION_X) / calibration[0]) );
			Y1.add(Math.round(target.getFeature(Feature.POSITION_Y) / calibration[1]) );
			frames.add(frame);
		}
		
	}
		
	private class SpotOverlay extends AbstractAnnotation {

		protected float lineThickness = 1.0f;
		protected float[] dash = new float[] { 1 };
		/** The spot collection this annotation should draw. */
		protected SpotCollection target;
		/** The color mapping of the target collection. */
		protected Map<Spot, Color> targetColor;
		
		public SpotOverlay() {
			this.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER);
			this.target = spotsToShow;
			this.targetColor = spotColor;
		}

		@Override
		public void draw(Graphics2D g2d) {
			
			if (!spotVisible || null == target )
				return;
			
			final int frame = imp.getFrame()-1;
			final float zslice = (imp.getSlice()-1) * calibration[2];
			
			g2d.setStroke(new BasicStroke((float) (lineThickness / canvas.getMagnification()), 
					BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f, dash , 0));

			float x, y, z, dz2;
			int apparentRadius;
			List<Spot> spots = target.get(frame);
			if (null == spots)
				return;
			for (Spot spot : spots) {
				if (null == spot)
					continue;
				g2d.setColor(targetColor.get(spot));
				x = spot.getFeature(Feature.POSITION_X);
				y = spot.getFeature(Feature.POSITION_Y);
				z = spot.getFeature(Feature.POSITION_Z);
				dz2 = (z - zslice) * (z - zslice);
				if (dz2 >= radius*radius)
					g2d.fillOval(Math.round(x/calibration[0]) - 2, Math.round(y/calibration[1]) - 2, 4, 4);
				else {
					apparentRadius = (int) Math.round( Math.sqrt(radius*radius - dz2) / calibration[0]); 
					g2d.drawOval(Math.round(x/calibration[0]) - apparentRadius, Math.round(y/calibration[1]) - apparentRadius, 
							2 * apparentRadius, 2 * apparentRadius);			
				}
			}
		}
	}
	
	private class HighlightSpotOverlay extends SpotOverlay {


		public HighlightSpotOverlay() {
			super();
			this.lineThickness = 2.0f;
			this.target = spotSelection;
			this.targetColor = spotSelectionColor;
		}
	}

	private class SpotEditOverlay extends SpotOverlay {

		public SpotEditOverlay() {
			super();
			this.lineThickness = 2.0f;
			this.dash = new float[] {4, 4};
			this.target = editedSpot;
			this.targetColor = editedSpotColor;
		}
	}
		
	private class HighlightTrackOverlay extends TrackOverlay {
		
		public HighlightTrackOverlay() {
			super(HIGHLIGHT_COLOR);
			this.lineThickness = 2.0f;
		}
	}
	
	
	private ImagePlus imp;
	private OverlayedImageCanvas canvas;
	private float[] calibration;
	/** Contains the track overlay objects. */
	private HashMap<Set<Spot>, TrackOverlay> wholeTrackOverlays = new HashMap<Set<Spot>, TrackOverlay>();
	private Feature currentFeature;
	private float featureMaxValue;
	private float featureMinValue;
	private Settings settings;
	private boolean trackVisible = true;
	private boolean spotVisible = true;
	private StackWindow window;
	// For highlight
	private SpotOverlay 			spotOverlay 			= new SpotOverlay();
	private HighlightSpotOverlay 	highlightSpotOverlay 	= new HighlightSpotOverlay();
	private SpotEditOverlay 		editOverlay 			= new SpotEditOverlay();
	private HighlightTrackOverlay 	highlightTrackOverlay;
	/** The spot currently being edited, empty if no spot is being edited. */
	private SpotCollection editedSpot;
	/** A mapping of the spots versus the color they must be drawn in, for the currently edited spot. */
	private Map<Spot, Color> editedSpotColor = new HashMap<Spot, Color>();;
	/** A mapping of the spots versus the color they must be drawn in. */
	private Map<Spot, Color> spotColor = new HashMap<Spot, Color>();
	/** A mapping of the spots versus the color they must be drawn in, for the spot selection */
	private Map<Spot, Color> spotSelectionColor = new HashMap<Spot, Color>();


	/*
	 * CONSTRUCTORS
	 */
	
	public HyperStackDisplayer(final Settings settings) {
		this.radius = settings.segmenterSettings.expectedRadius;
		this.imp = settings.imp;
		this.calibration = new float[] { settings.dx, settings.dy, settings.dz };
		this.settings = settings;
	}
	
		
	/*
	 * PUBLIC METHODS
	 */
	
	@Override
	public void highlightEdges(Set<DefaultWeightedEdge> edges) {
		Spot source, target;
		int frame;
		if (null != highlightTrackOverlay)
			canvas.removeOverlay(highlightTrackOverlay);
		highlightTrackOverlay = new HighlightTrackOverlay();
		for (DefaultWeightedEdge edge : edges) {
			source = trackGraph.getEdgeSource(edge);
			target = trackGraph.getEdgeTarget(edge);
			frame = -1;
			for (int key : spotsToShow.keySet())
				if (spots.get(key).contains(source)) {
					frame = key;
					break;
				}
			highlightTrackOverlay.addEdge(source, target, frame);
		}
		canvas.addOverlay(highlightTrackOverlay);
		imp.updateAndDraw();
	}
	

	@Override
	public void highlightSpots(SpotCollection spots) {
		spotSelection = spots;
		prepareHighlightSpots();
		imp.updateAndDraw();		
	}
	
	@Override
	public void highlightSpots(Collection<Spot> spots) {
		spotSelection = new SpotCollection();
		for (Spot spot : spots) 
			spotSelection.add(spot, spotsToShow.getFrame(spot));
		prepareHighlightSpots();
		imp.updateAndDraw();				
	}

	@Override
	public void centerViewOn(Spot spot) {
		int frame = - 1;
		for(int i : spotsToShow.keySet()) {
			List<Spot> spotThisFrame = spotsToShow.get(i);
			if (spotThisFrame.contains(spot)) {
				frame = i;
				break;
			}
		}
		if (frame == -1)
			return;
		int z = Math.round(spot.getFeature(Feature.POSITION_Z) / calibration[2] ) + 1;
		imp.setPosition(1, z, frame+1);
//		window.setPosition(1, z, frame+1);
	}
	
	@Override
	public void setTrackVisible(boolean displayTrackSelected) {
		trackVisible = displayTrackSelected;
	}
	
	@Override
	public void setSpotVisible(boolean displaySpotSelected) {
		spotVisible = displaySpotSelected;		
	}
	
	@Override
	public void render() {
		if (null == imp) {
			this.imp = NewImage.createByteImage("Empty", settings.width, settings.height, settings.nframes*settings.nslices, NewImage.FILL_BLACK);
			this.imp.setDimensions(1, settings.nslices, settings.nframes);
		}
		imp.setOpenAsHyperStack(true);
		canvas = new OverlayedImageCanvas(imp);
		window = new StackWindow(imp, canvas);
		window.show();
		canvas.addOverlay(highlightSpotOverlay);
		canvas.addOverlay(editOverlay);
		canvas.addMouseListener(this);
		
		refresh();
	}
	
	@Override
	public void setRadiusDisplayRatio(float ratio) {
		super.setRadiusDisplayRatio(ratio);
		prepareSpotOverlay();
		refresh();
	}
	
	@Override
	public void setColorByFeature(Feature feature) {
		currentFeature = feature;
		// Get min & max
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
		Float val;
		for (int key : spots.keySet()) {
			for (Spot spot : spots.get(key)) {
				val = spot.getFeature(feature);
				if (null == val)
					continue;
				if (val > max) max = val;
				if (val < min) min = val;
			}
		}
		featureMinValue = min;
		featureMaxValue = max;
		prepareSpotOverlay();
		refresh();
	}
		
	@Override
	public void refresh() {
		imp.updateAndDraw();
	}
		
	@Override
	public void setTrackGraph(SimpleWeightedGraph<Spot, DefaultWeightedEdge> trackGraph) {
		super.setTrackGraph(trackGraph);
		prepareWholeTrackOverlay();
	}
	
	@Override
	public void setSpotsToShow(SpotCollection spotsToShow) {
		super.setSpotsToShow(spotsToShow);
		prepareSpotOverlay();
	}
	
	@Override
	public void clear() {
		canvas.clearOverlay();
	}
	
	
	/*
	 * PRIVATE METHODS
	 */
	
	private final Spot getCLickLocation(final MouseEvent e) {
		final int ix = canvas.offScreenX(e.getX());
		final int iy =  canvas.offScreenX(e.getY());
		final float x = ix * calibration[0];
		final float y = iy * calibration[1];
		final float z = (imp.getSlice()-1) * calibration[2];
		return new SpotImp(new float[] {x, y, z});
	}

	/**
	 * Tune the highlight (selection) overlay.
	 */
	private void prepareHighlightSpots() {
		spotSelectionColor = new HashMap<Spot, Color>(spotSelection.getNSpots());
		for(Spot spot : spotSelection)
			spotSelectionColor.put(spot, HIGHLIGHT_COLOR);
		canvas.removeOverlay(highlightSpotOverlay);
		highlightSpotOverlay = new HighlightSpotOverlay();
		canvas.addOverlay(highlightSpotOverlay);
	}

	
	private void prepareSpotOverlay() {
		if (null == spotsToShow)
			return;
		canvas.removeOverlay(spotOverlay);
		spotOverlay = new SpotOverlay();
		Float val;
		for(Spot spot : spotsToShow) {
			val = spot.getFeature(currentFeature);
			if (null == currentFeature || null == val)
				spotColor.put(spot, color);
			else
				spotColor.put(spot, colorMap.getPaint((val-featureMinValue)/(featureMaxValue-featureMinValue)) );
		}
		canvas.addOverlay(spotOverlay);
	}
	
	private void prepareWholeTrackOverlay() {
		if (null == tracks)
			return;
		for (TrackOverlay wto : wholeTrackOverlays.values()) 
			canvas.removeOverlay(wto);
		wholeTrackOverlays.clear();
		for (Set<Spot> track : tracks)
			wholeTrackOverlays.put(track, new TrackOverlay(trackColors.get(track)));
		
		Spot source, target;
		Set<DefaultWeightedEdge> edges = trackGraph.edgeSet();
		int frame;
		for (DefaultWeightedEdge edge : edges) {
			source = trackGraph.getEdgeSource(edge);
			target = trackGraph.getEdgeTarget(edge);
			// Find to what frame it belongs to
			frame = -1;
			for (int key : spotsToShow.keySet())
				if (spots.get(key).contains(source)) {
					frame = key;
					break;
				}
			for (Set<Spot> track : tracks) {
				if (track.contains(source)) {
					wholeTrackOverlays.get(track).addEdge(source, target, frame);
					break;
				}
			}
		}
		
		for (TrackOverlay wto : wholeTrackOverlays.values())
			canvas.addOverlay(wto);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		final Spot clickLocation = getCLickLocation(e);
		final int frame = imp.getFrame() - 1;		
		Spot target = spotsToShow.getClosestSpot(clickLocation, frame);
		
		// Check desired behavior
		switch (e.getClickCount()) {
		case 1: {
			// Change selection
			// only if we are nut currently editing
			if (null != editedSpot)
				return;
			final int addToSelectionMask = MouseEvent.SHIFT_DOWN_MASK;
			final int flag;
			if ((e.getModifiersEx() & addToSelectionMask) == addToSelectionMask) 
				flag = MODIFY_SELECTION_FLAG;
			else 
				flag = REPLACE_SELECTION_FLAG;
			spotSelectionChanged(target, frame, flag);
			break;
		}
		
		case 2: {
			// Edit spot
			
			// Empty current selection
			spotSelectionChanged(null, frame, REPLACE_SELECTION_FLAG);
			
			if (null == editedSpot) {
				// No spot is currently edited, we pick one to edit
				System.out.println("Entering edit mode");// DEBUG
				if (target.squareDistanceTo(clickLocation) > radius*radius) {
					// Create a new spot if not inside one
					target = clickLocation;
					System.out.println("Creating new spot");// DEBUG
				} else {
					System.out.println("Editing spot "+target.getName());// DEBUG
				}
				editedSpot = new SpotCollection();
				editedSpot.add(target, frame);
				editedSpotColor.clear();
				editedSpotColor.put(target, HIGHLIGHT_COLOR);
				spots.remove(target, frame);
				spotsToShow.remove(target, frame);
				spotSelection.remove(target, frame);
				editOverlay = new SpotEditOverlay();
				canvas.addOverlay(editOverlay);
				
			} else {
				// We leave editing mode
				System.out.println("Leaving edit mode");// DEBUG
				target = editedSpot.iterator().next();
				spots.add(target, frame); // Only one spot in this collection anyway
				spotsToShow.add(target, frame);
				editedSpot = null;
				canvas.removeOverlay(editOverlay);
				prepareSpotOverlay();
				prepareWholeTrackOverlay();
				
			}
			break;
		}
		}
	} 


	@Override
	public void mousePressed(MouseEvent e) {}


	@Override
	public void mouseReleased(MouseEvent e) {}


	@Override
	public void mouseEntered(MouseEvent e) {}


	@Override
	public void mouseExited(MouseEvent e) {}


}