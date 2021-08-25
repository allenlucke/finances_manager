package com.Allen.SpringFinancesServer.Security;

import java.util.ArrayList;

import com.Allen.SpringFinancesServer.User.UserDao;
import com.Allen.SpringFinancesServer.User.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtUserDetailsService implements UserDetailsService {

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordEncoder bcryptEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("Username: " + username);
        try{
            UserModel checkUser = userDao.getUserByUsername(username);
            return new User(checkUser.getUserName(), checkUser.getPassword(),
                    new ArrayList<>());
        }
        catch (Exception e){
        throw new UsernameNotFoundException("User not found with username: " + username);
        }
    }

    public UserModel addUser(UserModel user) {
        user.setPassword(bcryptEncoder.encode(user.getPassword()));

        UserModel registeredUser = userDao.addUserReturnUser(user);
        return registeredUser;
    }
}
