/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Manages visit booking workflows for pets belonging to owners.
 * Interacts with {@link OwnerRepository} to load owner aggregates and persist
 * visit additions consistently.
 * Sustainability: centralized loading, validation, and persistence reduce
 * duplication, improve readability, lower onboarding effort, and help keep
 * long-term maintenance costs predictable.
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 * @author Dave Syer
 * @author Wick Dynex
 */
@Controller
class VisitController {

	private final OwnerRepository owners;

	/**
	 * Creates the controller with its required repository dependency.
	 * @param owners repository used for loading and persisting owners/visits
	 */
	public VisitController(OwnerRepository owners) {
		this.owners = owners;
	}

	/**
	 * Disallows binding of identifier fields to protect data integrity.
	 * @param dataBinder binder used to configure allowed and disallowed fields
	 */
	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		// Prevent mass assignment of identifiers.
		dataBinder.setDisallowedFields("id", "*.id");
	}

	/**
	 * Called before each and every @RequestMapping annotated method. 2 goals: - Make sure
	 * we always have fresh data - Since we do not use the session scope, make sure that
	 * Pet object always has an id (Even though id is not part of the form fields)
	 * 
	 * Prepares a {@link Visit} instance and loads the associated owner and pet
	 * for visit booking workflows.
	 * @param ownerId owner identifier from the route
	 * @param petId pet identifier from the route
	 * @param model model used to expose owner and pet to the view
	 * @return new visit instance associated with the resolved pet
	 * @throws IllegalArgumentException if the owner or pet cannot be found
	 */
	@ModelAttribute("visit")
	public Visit loadPetWithVisit(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
			Map<String, Object> model) {
		// Centralized loading keeps request handling consistent and reusable.
		Optional<Owner> optionalOwner = owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));

		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException(
					"Pet with id " + petId + " not found for owner with id " + ownerId + ".");
		}
		// Model population is explicit to avoid hidden state and simplify testing.
		model.put("pet", pet);
		model.put("owner", owner);

		// Create a new visit instance for the form binding lifecycle.
		Visit visit = new Visit();
		pet.addVisit(visit);
		return visit;
	}

	// Spring MVC calls method loadPetWithVisit(...) before initNewVisitForm is
	// called

	/**
	 * Displays the visit creation form.
	 * @return view name for the create/update visit form
	 */
	@GetMapping("/owners/{ownerId}/pets/{petId}/visits/new")
	public String initNewVisitForm() {
		return "pets/createOrUpdateVisitForm";
	}

	// Spring MVC calls method loadPetWithVisit(...) before processNewVisitForm is
	// called
	
	/**
	 * Validates and persists a new visit for a pet.
	 * @param owner resolved owner for the current request
	 * @param petId identifier of the pet receiving the visit
	 * @param visit bound visit entity from the form
	 * @param result validation results for the submitted visit
	 * @param redirectAttributes attributes used to convey user feedback
	 * @return redirect to the owner details page or the form view on errors
	 */
	@PostMapping("/owners/{ownerId}/pets/{petId}/visits/new")
	public String processNewVisitForm(@ModelAttribute Owner owner, @PathVariable int petId, @Valid Visit visit,
			BindingResult result, RedirectAttributes redirectAttributes) {
		// Validation gate reduces invalid data entry and future correction work.
		if (result.hasErrors()) {
			return "pets/createOrUpdateVisitForm";
		}

		// Persist via owner aggregate to keep visit lifecycle consistent.
		owner.addVisit(petId, visit);
		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "Your visit has been booked");
		return "redirect:/owners/{ownerId}";
	}

}
