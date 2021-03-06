/*****************************************************************************
 *                        Copyright Yumetech, Inc (c) 2009
 *                               Java Source
 *
 * This source is licensed under the GNU LGPL v2.1
 * Please read http://www.gnu.org/copyleft/lgpl.html for more information
 *
 * This software comes with the standard NO WARRANTY disclaimer for any
 * purpose. Use it at your own risk. If there's a problem you get to fix it.
 *
 ****************************************************************************/

package org.chefx3d.view.awt.av3d;

// External imports
import javax.vecmath.AxisAngle4f;
import javax.vecmath.Matrix3f;
import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import org.j3d.aviatrix3d.ViewEnvironment;

import org.j3d.device.input.TrackerState;

import org.j3d.util.MatrixUtils;

// Local Imports
import org.chefx3d.model.Entity;
import org.chefx3d.model.ChangePropertyTransientCommand;
import org.chefx3d.model.CommandController;
import org.chefx3d.model.ViewpointEntity;

/**
 * Implementation of NavigationMode that produces a Pan-Zoom behavior
 * in an orthographic Viewport
 * 
 * @author Rex Melton
 * @version $Revision: 1.3 $
 */	
class OrthoPanZoomNavigationModeController implements NavigationModeController {
	
	/** Hardcoding a static zoom scalar instead of dynamically adjusting it */
	private static final float DEFAULT_STATIC_ZOOM_SCALAR = 1.1f;
	
	/** The mode name */
	private static final String name = "Pan-Zoom";
	
	/** The ViewpointEntity */
	private ViewpointEntity ve;
	
	/** Command Execution manager */
	private CommandController cmdCntl;
	
	/** The navigation collision manager */
	private NavigationCollisionManager collisionManager;
	
	/** The navigation status manager */
	private NavigationStatusManager statusManager;
	
	/** The active ViewpointData */
	private ViewpointData data;
	
	/** The active ViewEnvironment */
	private ViewEnvironment viewEnv;
	
	/** The initial position */
	private float[] startPosition;
	
	/** The current position */
	private float[] currentPosition;
	
	/** Scratch variable containing the direction of the move */
	private float[] direction;
	
	/** The view frustum */
	private double[] frustum;
	
	/** The up vector of the view matrix */
	private Vector3f upVector;
	
	/** The horizontal vector of the view matrix */
	private Vector3f rightVector;
	
	/** Scratch matrix to retrieve the Viewpoint Transform's matrix */
	private Matrix4f startViewMatrix;
	
	/** Scratch matrix to calculate the new Viewpoint Transform's matrix */
	private Matrix4f destViewMatrix;
	
	/** Scratch Vector used to read the position value from the viewMatrix */
	private Vector3f positionVector;
	
	/** Center of rotation for examine and look at modes */
	private Point3f centerOfRotation;
	
	/** Scratch array for view matrix values */
	private float[] array;
	
	/**
	 * Constructor
	 * 
	 * @param data The ViewpointData to manipulate
	 * @param viewEnv The ViewEnvironment to manipulate
	 * @param cmdCntl The CommandController
	 * @param ve The ViewpointEntity
	 * @param collisionManager The navigation collision detector
	 * @param statusManager The navigation status reporter
	 */
	OrthoPanZoomNavigationModeController(
		ViewpointData data, 
		ViewEnvironment viewEnv,  
		CommandController cmdCntl,
		ViewpointEntity ve, 
		NavigationCollisionManager collisionManager, 
		NavigationStatusManager statusManager) {
		
		this.data = data;
		this.viewEnv = viewEnv;
		this.cmdCntl = cmdCntl;
		this.ve = ve;
		this.collisionManager = collisionManager;
		this.statusManager = statusManager;
		
		startPosition = new float[3];
		currentPosition = new float[3];
		direction = new float[3];
		frustum = new double[6];
		
		upVector = new Vector3f();
		rightVector = new Vector3f();
		
		startViewMatrix = new Matrix4f();
		destViewMatrix = new Matrix4f();
		positionVector = new Vector3f();
		centerOfRotation = new Point3f();
		
		array = new float[16];
	}
	
	//---------------------------------------------------------------
	// Methods defined by NavigationMode
	//---------------------------------------------------------------
	
	/**
	 * Return the text identifier of this mode
	 * 
	 * @return The text identifier of this mode
	 */
	public String getName() {
		return(name);
	}
	
	/**
	 * Navigation has started, initialize the mode state
	 * 
	 * @param state The container for device input parameters
	 */
	public void start(TrackerState state) {
		startPosition[0] = state.devicePos[0];
		startPosition[1] = state.devicePos[1];
		startPosition[2] = state.devicePos[2];
	}
	
	/**
	 * Process the movement for this mode
	 *
	 * @param state The container for device input parameters
	 */
	public void move(TrackerState state) {
		
		currentPosition[0] = state.devicePos[0];
		currentPosition[1] = state.devicePos[1];
		currentPosition[2] = state.devicePos[2];
		
		if (state.ctrlModifier) {
			
			direction[0] = 0;
			direction[1] = 0;
			direction[2] = (currentPosition[1] - startPosition[1]);
			
			if( direction[2] != 0 ) {
				
				// TODO: hard coded scale factor should be 
				// replaced with something more dynamic
				float scale = DEFAULT_STATIC_ZOOM_SCALAR;
				if (direction[2] < 0) {
					scale = 1/scale;
				}
				
				viewEnv.getViewFrustum(frustum);
				
				frustum[0] *= scale;
				frustum[1] *= scale;
				frustum[2] *= scale;
				frustum[3] *= scale;
				
				// 
				// Set the clipping planes
				//
				viewEnv.setOrthoParams(frustum[0], frustum[1], frustum[2], frustum[3]);
				
				ChangePropertyTransientCommand cptc = 
					new ChangePropertyTransientCommand(
					ve, 
					Entity.DEFAULT_ENTITY_PROPERTIES, 
					ViewpointEntity.ORTHO_PARAMS_PROP,
					frustum,
					null);
				cmdCntl.execute(cptc);
			}
		} else {
			
			direction[0] = (startPosition[0] - currentPosition[0]);
			direction[1] = (currentPosition[1] - startPosition[1]);
			direction[2] = 0;
			
			boolean collisionDetected = false;
			if( (direction[0] != 0) || (direction[1] != 0) ) {
				
				viewEnv.getViewFrustum(frustum);
				
				float f_width = (float)(frustum[1] - frustum[0]);
				float f_height = (float)(frustum[3] - frustum[2]);

				direction[0] *= f_width;
				direction[1] *= f_height;
				
				data.viewpointTransform.getTransform(startViewMatrix);
				startViewMatrix.get(positionVector);
				
				upVector.x = startViewMatrix.m01;
				upVector.y = startViewMatrix.m11;
				upVector.z = startViewMatrix.m21;
				upVector.normalize();
				
				upVector.scale(direction[1]);
				positionVector.add(upVector);
				
				rightVector.x = startViewMatrix.m00;
				rightVector.y = startViewMatrix.m10;
				rightVector.z = startViewMatrix.m20;
				rightVector.normalize();
				
				rightVector.scale(direction[0]);
				positionVector.add(rightVector);
				
				destViewMatrix.set(startViewMatrix);
				destViewMatrix.setTranslation(positionVector);
				
				collisionDetected = 
					collisionManager.checkCollision(startViewMatrix, destViewMatrix);
				
				if (!collisionDetected) {
					AV3DUtils.toArray(destViewMatrix, array);
					ChangePropertyTransientCommand cptc = new ChangePropertyTransientCommand(
						ve, 
						Entity.DEFAULT_ENTITY_PROPERTIES, 
						ViewpointEntity.VIEW_MATRIX_PROP,
						array,
						null);
					cmdCntl.execute(cptc);
					if (statusManager != null) {
						statusManager.fireViewMatrixChanged(destViewMatrix);
					}
				}
			}
		}
		startPosition[0] = currentPosition[0];
		startPosition[1] = currentPosition[1];
		startPosition[2] = currentPosition[2];
	}
	
	/**
	 * Navigation has completed, terminate any processing
	 *
	 * @param state The container for device input parameters
	 */
	public void finish(TrackerState state) {
		
	}
	
	/**
	 * Return the current center-of-rotation in the argument array.
	 * If the arry is null or undersized, a new array will be 
	 * allocated
	 *
	 * @param cor An array to initialize with the value.
	 * @return the current center-of-rotation
	 */
	public float[] getCenterOfRotation(float[] cor) {
		if ((cor == null) || (cor.length < 3)) {
			cor = new float[3];
		}
		centerOfRotation.get(cor);
		return(cor);
	}
	
	/**
	 * Set the current center-of-rotation.
	 *
	 * @param cor An array containing the center-of-rotation.
	 */
	public void setCenterOfRotation(float[] cor) {
		centerOfRotation.set(cor);
	}
}
