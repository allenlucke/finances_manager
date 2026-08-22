package com.Allen.SpringFinancesServer.Security;

import com.Allen.SpringFinancesServer.User.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;


@RestController
@CrossOrigin
public class JwtAuthenticationController {

    private static final String CLASS_NAME = "JwtAuthenticationController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private JwtUserDetailsService userDetailsService;

    @RequestMapping(value = "/authenticate", method = RequestMethod.POST)
    public ResponseEntity<?> createAuthenticationToken(@RequestBody JwtRequest authenticationRequest) throws Exception {

        final String methodName = "createAuthenticationToken() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        authenticate(authenticationRequest.getUsername(), authenticationRequest.getPassword());

        final UserDetails userDetails = userDetailsService
                .loadUserByUsername(authenticationRequest.getUsername());

        final String token = jwtTokenUtil.generateToken(userDetails);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return ResponseEntity.ok(new JwtResponse(token));
    }

    @RequestMapping(value = "register", method = RequestMethod.POST)
    public ResponseEntity<?> addUser(@RequestBody UserModel user) throws Exception {
        final String methodName = "addUser() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return ResponseEntity.ok(userDetailsService.addUser(user));
    }

    private void authenticate(String username, String password) throws Exception {
        final String methodName = "authenticate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        } catch (DisabledException e) {
            LOGGER.warn(CLASS_NAME + METHOD_EXITING + methodName + "User is disabled");
            throw new Exception("USER_DISABLED", e);
        } catch (BadCredentialsException e) {
            LOGGER.warn(CLASS_NAME + METHOD_EXITING + methodName + "Invalid security credentials");
            throw new Exception("INVALID_CREDENTIALS", e);
        }
    }
}