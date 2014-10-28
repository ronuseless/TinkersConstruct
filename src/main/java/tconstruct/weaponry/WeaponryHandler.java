package tconstruct.weaponry;

import tconstruct.weaponry.ammo.ArrowAmmo;
import tconstruct.weaponry.ammo.BoltAmmo;
import tconstruct.library.tools.DualMaterialToolPart;
import tconstruct.library.weaponry.ArrowShaftMaterial;
import tconstruct.library.weaponry.BowBaseAmmo;
import tconstruct.library.weaponry.ProjectileWeapon;
import tconstruct.library.weaponry.IAmmo;
import tconstruct.weaponry.weapons.Crossbow;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import tconstruct.library.TConstructRegistry;
import tconstruct.library.crafting.ToolBuilder;
import tconstruct.library.event.ToolBuildEvent;
import tconstruct.library.event.ToolCraftEvent;
import tconstruct.library.tools.*;

public class WeaponryHandler {
    // Provides ammo-items with the necessary NBT
    @SubscribeEvent
    public void onAmmoCrafted(ToolCraftEvent.NormalTool event)
    {
        if(!(event.tool instanceof IAmmo))
            return;

        NBTTagCompound tags = event.toolTag.getCompoundTag("InfiTool");

        // calculate its stats
        if(event.tool instanceof ArrowAmmo)
        {
            // arrows work like this:
            // the head is responsible for the damage, but also adds weight
            // the shaft defines how fragile the arrow is, and also adds to the weight a bit. But mostly the fragility.
            // the fletching defines the accuracy of the arrow and adds a bit breakchance. Mostly there for the durability modifier because availability

            // Shortbows work better with lighter arrows
            // while Longbows require a bit heavier arrows, the lighter the arrow the more impact the accuracy has otherwise

            // summa sumarum: heavier arrows fall faster (less range) but accuracy has less impact

            // the materials
            ToolMaterial head = TConstructRegistry.getMaterial(tags.getInteger("Head"));
            ArrowMaterial arrow = TConstructRegistry.getArrowMaterial(tags.getInteger("Head"));
            ArrowShaftMaterial shaft = (ArrowShaftMaterial) TConstructRegistry.getCustomMaterial(tags.getInteger("Handle"), ArrowShaftMaterial.class);
            FletchingMaterial fletching = (FletchingMaterial) TConstructRegistry.getCustomMaterial(tags.getInteger("Accessory"), FletchingMaterial.class);

            // todo: fix leaf fletching
            if(fletching == null)
                fletching = (FletchingMaterial) TConstructRegistry.getCustomMaterial(tags.getInteger("Accessory"), FletchlingLeafMaterial.class);

            int durability = (int)((float)head.durability() * shaft.durabilityModifier * fletching.durabilityModifier);
            float weight = arrow.mass + shaft.weight/2f;
            float accuracy = fletching.accuracy;
            float breakChance = shaft.fragility + fletching.breakChance;

            setAmmoData(tags, durability, weight, breakChance, accuracy, head.shoddy(), head.reinforced());
        }
        else if(event.tool instanceof BoltAmmo)
        {
            // bolts work like ammos, but have more weight as they have 2 main materials
            // Crossbows work better with heavier bolts

            // the materials
            ToolMaterial headMat = TConstructRegistry.getMaterial(tags.getInteger("Head"));
            ToolMaterial coreMat = TConstructRegistry.getMaterial(tags.getInteger("Handle"));
            ArrowMaterial head = TConstructRegistry.getArrowMaterial(tags.getInteger("Head"));
            ArrowMaterial core = TConstructRegistry.getArrowMaterial(tags.getInteger("Handle"));
            FletchingMaterial fletching = (FletchingMaterial) TConstructRegistry.getCustomMaterial(tags.getInteger("Accessory"), FletchingMaterial.class);

            // todo: fix leaf fletching
            if(fletching == null)
                fletching = (FletchingMaterial) TConstructRegistry.getCustomMaterial(tags.getInteger("Accessory"), FletchlingLeafMaterial.class);

            int durability = (int)((float)headMat.durability() * coreMat.handleDurability() * fletching.durabilityModifier);
            float weight = head.mass + core.mass;
            float accuracy = fletching.accuracy;
            float breakChance = fletching.breakChance*3;
            float shoddy = (headMat.shoddy() + coreMat.shoddy())/2f;
            int reinforced = Math.max(headMat.reinforced(), coreMat.reinforced());

            setAmmoData(tags, durability, weight, breakChance, accuracy, shoddy, reinforced);
        }

        // now that durability has been handled...
        // fill the ammo full and at the same time provide the missing NBT tag
        IAmmo ammoItem = (IAmmo) event.tool;
        tags.setInteger("Ammo", ammoItem.getMaxAmmo(tags));
    }

    @SubscribeEvent
    public void onProjectileWeaponCrafted(ToolCraftEvent.NormalTool event)
    {
        if(!(event.tool instanceof ProjectileWeapon))
            return;

        NBTTagCompound tags = event.toolTag.getCompoundTag("InfiTool");

        int drawSpeed = 0;
        float flightSpeed = 0;

        BowMaterial top;
        BowMaterial bottom;
        BowstringMaterial string;

        if(event.tool instanceof BowBaseAmmo) {
            top = TConstructRegistry.getBowMaterial(tags.getInteger("Head"));
            bottom = TConstructRegistry.getBowMaterial(tags.getInteger("Accessory"));
            string = (BowstringMaterial) TConstructRegistry.getCustomMaterial(tags.getInteger("Handle"), BowstringMaterial.class);

            drawSpeed = (int) ((top.drawspeed + bottom.drawspeed) / 2f * string.drawspeedModifier);
            flightSpeed = (top.flightSpeedMax + bottom.flightSpeedMax)/2 * string.flightSpeedModifier;
        }
        else if(event.tool instanceof Crossbow)
        {
            top = TConstructRegistry.getBowMaterial(tags.getInteger("Head"));
            string = (BowstringMaterial) TConstructRegistry.getCustomMaterial(tags.getInteger("Accessory"), BowstringMaterial.class);

            drawSpeed = (int) ((float)top.drawspeed * string.drawspeedModifier);
            flightSpeed = (top.flightSpeedMax * string.flightSpeedModifier);
        }
        else
            return;

        // enchanted fabric
        if (tags.getInteger("Handle") == 1) {
            int modifiers = tags.getInteger("Modifiers");
            modifiers += 1;
            tags.setInteger("Modifiers", modifiers);
        }

        tags.setInteger("DrawSpeed", drawSpeed);
        tags.setInteger("BaseDrawSpeed", drawSpeed); // used to calculate correct speed increase with redstone modifier
        tags.setFloat("FlightSpeed", flightSpeed);
    }


    // arrows use custom materials. But we don't allow the creation of those items
    // we therefore replace the items with their toolpart counterparts here
    @SubscribeEvent
    public void buildArrow(ToolBuildEvent event)
    {
        if(event.headStack == null || event.handleStack == null || event.accessoryStack == null)
            return;

        // are we building an arrow?
        CustomMaterial mat = TConstructRegistry.getCustomMaterial(event.handleStack, ArrowShaftMaterial.class);
        if(mat == null)
            return;
        Item extra = event.extraStack != null ? event.extraStack.getItem() : null;
        ToolCore tool = ToolBuilder.instance.getMatchingRecipe(event.headStack.getItem(), mat.craftingItem.getItem(), event.accessoryStack.getItem(), extra);

        // it's an arrow!
        if(tool == TinkerWeaponry.arrowAmmo)
            event.handleStack = mat.craftingItem.copy();
    }

    // bolts require special treatment because of their dual-material cores
    @SubscribeEvent
    public void buildBolt(ToolBuildEvent event)
    {
        if(event.headStack == null || event.handleStack == null)
            return;

        if(event.headStack.getItem() != TinkerWeaponry.partBolt)
            return;

        // split the bolt into its two parts
        ItemStack bolt1 = event.headStack.copy();
        ItemStack bolt2 = event.headStack;
        ItemStack fletching = event.handleStack;


        // set the correct material on the 2nd part
        DualMaterialToolPart dualPart = (DualMaterialToolPart) bolt2.getItem();
        bolt1.setItemDamage(dualPart.getMaterialID2(bolt1));

        // update the part positions xX
        event.headStack = bolt1;
        event.handleStack = bolt2;
        event.accessoryStack = fletching;
    }

    private void setAmmoData(NBTTagCompound tags, int durability, float weight, float breakChance, float accuracy, float shoddy, int reinforced)
    {
        tags.setInteger("TotalDurability", durability);
        tags.setFloat("Mass", weight);
        tags.setFloat("BreakChance", breakChance);
        tags.setFloat("Accuracy", accuracy);
        tags.setFloat("Shoddy", shoddy); // we could actually always set this to 0 since it has zero impact on ammo
        tags.setInteger("Unbreaking", reinforced);
    }
}
