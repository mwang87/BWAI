package starcraftbot.proxybot.bot;

import java.util.ArrayList;

import com.sun.org.apache.bcel.internal.generic.GETSTATIC;

import starcraftbot.proxybot.Game;
import starcraftbot.proxybot.Constants.Order;
import starcraftbot.proxybot.Constants.Race;
import starcraftbot.proxybot.wmes.ChokePointWME;
import starcraftbot.proxybot.wmes.UnitTypeWME;
import starcraftbot.proxybot.wmes.UnitTypeWME.UnitType;
import starcraftbot.proxybot.wmes.unit.PlayerUnitWME;
import starcraftbot.proxybot.wmes.unit.UnitWME;
/**
 * Example implementation of the StarCraftBot.
 * 
 * This build will tell workers to mine, build additional workers,
 * and build additional supply units.
 */
public class ExampleStarCraftBot implements StarCraftBot {

	/** specifies that the agent is running */
	boolean running = true;
	
	/**
	 * Starts the bot.
	 * 
	 * The bot is now the owner of the current thread.
	 */
	public void start(Game game) {

		// run until told to exit
		while (running) {
			try {
				Thread.sleep(1000);
			}
			catch (Exception e) {}

			// start mining
			for (UnitWME unit : game.getPlayerUnits()) {
				if(unit.getTypeID() != UnitTypeWME.Terran_SCV)
					continue;
				if (unit.getOrder() == Order.PlayerGuard.ordinal()) {

					int patchID = -1;
					double closest = Double.MAX_VALUE;
					
					for (UnitWME minerals : game.getMinerals()) {
						double dx = unit.getX() - minerals.getX();
						double dy = unit.getY() - minerals.getY();
						double dist = Math.sqrt(dx*dx + dy*dy); 
			
						if (dist < closest) {
							patchID = minerals.getID();
							closest = dist;
						}
					}					
					
					if (patchID != -1) {
						game.getCommandQueue().rightClick(unit.getID(), patchID);
					}
				}				
			}		
			
			int numSCVs = getNumberOfSCVs(game);
			buildMarines(game);
			buildBarracks(game);
			moveMarines(game);
			System.out.println("We have "+numSCVs + " SCVs");
			// build more workers
			if (game.getPlayer().getMinerals() >= 50 && numSCVs <= 20) {
				int workerType = UnitTypeWME.getWorkerType(game.getPlayerRace());

				// morph a larva into a worker
				if (game.getPlayerRace() == Race.Zerg.ordinal()) {
					/*for (UnitWME unit : game.getPlayerUnits()) {
						if (unit.getTypeID() == UnitType.Zerg_Larva.ordinal()) {
							game.getCommandQueue().morph(unit.getID(), workerType);
						}
					}*/
					//we ignore if we are zerg
					return;
				}
				// train a worker
				else {				
					int centerType = UnitTypeWME.getCenterType(game.getPlayerRace());
	
					for (UnitWME unit : game.getPlayerUnits()) {
						if (unit.getTypeID() == centerType) {
							game.getCommandQueue().train(unit.getID(), workerType);
						}
					}
				}
			}
			
			
			
			
			// build more supply
			if (game.getPlayer().getMinerals() >= 100 && 
					game.getPlayer().getSupplyUsed() >= (game.getPlayer().getSupplyTotal() - 2) ) {
				int supplyType = UnitTypeWME.getSupplyType(game.getPlayerRace());

				// morph a larva into a supply 
				if (game.getPlayerRace() == Race.Zerg.ordinal()) {
					for (UnitWME unit : game.getPlayerUnits()) {
						if (unit.getTypeID() == UnitType.Zerg_Larva.ordinal()) {
							game.getCommandQueue().morph(unit.getID(), supplyType);
						}
					}						
				}
				// build a farm
				else {
					int workerType = UnitTypeWME.getWorkerType(game.getPlayerRace());
					for (UnitWME unit : game.getPlayerUnits()) {
						if (unit.getTypeID() == workerType) {
							
							// pick a random spot near the worker
							game.getCommandQueue().build(unit.getID(), 
									unit.getX() + (int)(-10.0 + Math.random() * 20.0), 
									unit.getY() + (int)(-10.0 + Math.random() * 20.0), 
									supplyType);							
							break;
						}
					}
				}
			}
		}
	}
	
	private int getNumberOfSCVs(Game game){
		ArrayList<PlayerUnitWME> playerUnits = game.getPlayerUnits();
		int SCVcount = 0;
		for(PlayerUnitWME unit : playerUnits){
			if(unit.getIsWorker())
				SCVcount++;
		}
		return SCVcount;
	}
	
	private void buildMarines(Game game){
		if(game.getPlayer().getMinerals() < 200 || getNumberOfSCVs(game) < 10)
			return;
		
		if(getNumBarracks(game) == 0)
			return;
		
		System.out.println("Building some Marines");
		for (UnitWME unit : game.getPlayerUnits()) {
			if(unit.getTypeID() == UnitTypeWME.Terran_Barracks){
				game.getCommandQueue().train(unit.getID(), UnitTypeWME.Terran_Marine);
			}
		}
		
	}
	
	private void buildBarracks(Game game){
		//determine if barracks was already built
		System.out.println("Build some Barracks update");
		if(getNumBarracks(game) > 2)
			return;
		
		if(game.getPlayer().getMinerals() > 150 && getNumberOfSCVs(game) > 8){
			int workerType = UnitTypeWME.getWorkerType(game.getPlayerRace());
			for (UnitWME unit : game.getPlayerUnits()) {
				if (unit.getTypeID() == workerType) {
					
					// pick a random spot near the worker
					game.getCommandQueue().build(unit.getID(), 
							unit.getX() + (int)(-10.0 + Math.random() * 20.0), 
							unit.getY() + (int)(-10.0 + Math.random() * 20.0), 
							UnitTypeWME.Terran_Barracks);							
					break;
				}
			}
		}
	}
	
	private void moveMarines(Game game){
		//if there arent any enemies, we just move marines to choke
		//finding location of commandcenter
		int homeX = 0;
		int homeY = 0;
		int distance;
		int targetX;
		int targetY;
		for(UnitWME unit : game.getPlayerUnits()){
			if(unit.getIsCenter()){
				homeX = unit.getX();
				homeY = unit.getY();
				break;
			}	
		}
		//if there are enemy units on the screen, attack them
		if(game.getEnemyUnits().size() > 0){
			//then there are enemy units, attack them
			UnitWME enemy = game.getEnemyUnits().get(0);
			issueMoveMarines(game, enemy.getX(), enemy.getY());
			return;
		}
		
		
		if(game.getChokePoints().size() == 0){
			//roam some random place
			targetX = (int)(homeX + -15 + Math.random()*30);
			targetY = (int)(homeY + -15 + Math.random()*30);
			issueMoveMarines(game, targetX, targetY);
		}else{
			ChokePointWME closestChoke = game.getChokePoints().get(0);
			distance = (closestChoke.getX() - homeX)
					* (closestChoke.getX() - homeX)
					+ (closestChoke.getY() - homeY)
					* (closestChoke.getY() - homeY);
			for (ChokePointWME choke : game.getChokePoints()) {
				int newDistance = (choke.getX() - homeX)
						* (choke.getX() - homeX) + (choke.getY() - homeY)
						* (choke.getY() - homeY);
				if (newDistance < distance) {
					distance = newDistance;
					closestChoke = choke;
				}
			}
			targetX = closestChoke.getX();
			targetY = closestChoke.getY();
			issueMoveMarines(game, targetX, targetY);
			return;
		}
		
		//we have the closest choke
		//move marienes to this location
		
	}
	
	private void issueMoveMarines(Game game, int targetX, int targetY){
		System.out.println("Moving Marines to :" + targetX + " " + targetY);
		for(UnitWME unit : game.getPlayerUnits()){
			//if within certain distance of choke, don't bother to move there
			if(unit.getTypeID() == UnitTypeWME.Terran_Marine){
				System.out.println("Marine Here");
				if(!isWithinDistance(unit.getX(), unit.getY(), targetX, targetY , 3)){
					System.out.println("Moving Out");
					game.getCommandQueue().attackMove(unit.getID(), targetX, targetY);
				}
			}
		}
	}
	
	private boolean isWithinDistance(int x1, int y1, int x2, int y2, int distance){
		if((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2) <= distance*distance)
			return true;
		return false;
	}
	
	private int getNumBarracks(Game game){
		int barracksCount = 0;
		for(PlayerUnitWME unit : game.getPlayerUnits()){
			if(unit.getTypeID() == UnitTypeWME.Terran_Barracks)
				barracksCount++;
		}
		return barracksCount;
	}

	/**
	 * Tell the main thread to quit.
	 */
	public void stop() {
		running = false;
	}
}
